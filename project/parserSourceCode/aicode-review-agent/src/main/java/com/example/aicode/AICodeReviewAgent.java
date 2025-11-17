package com.example.aicode;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.puppycrawl.tools.checkstyle.Checker;
import com.puppycrawl.tools.checkstyle.DefaultConfiguration;
import com.puppycrawl.tools.checkstyle.api.AuditEvent;
import com.puppycrawl.tools.checkstyle.api.AuditListener;

import net.sourceforge.pmd.PMDConfiguration;
import net.sourceforge.pmd.PmdAnalysis;
import net.sourceforge.pmd.Report;
import net.sourceforge.pmd.RuleSet;
import net.sourceforge.pmd.RuleSetLoader;

public class AICodeReviewAgent {

    public static class Issue {
        public String tool;
        public String file;
        public int line;
        public String message;
        public String severity;
        public String rule;

        public Issue(String tool, String file, int line, String message, String severity, String rule) {
            this.tool = tool;
            this.file = file;
            this.line = line;
            this.message = message;
            this.severity = severity;
            this.rule = rule;
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("provide a java source code directory");
            System.exit(1);
        }

        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir)) {
            throw new IllegalArgumentException("path does not exist: " + sourceDir);
        }

        List<Issue> issues = new ArrayList<>();
        issues.addAll(runPMD(sourceDir));
        issues.addAll(runCheckstyle(sourceDir));

        ObjectMapper mapper = new ObjectMapper();
        System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(issues));
    }

    private static List<Issue> runPMD(Path sourceDir) throws Exception {
        List<Issue> issues = new ArrayList<>();

        PMDConfiguration config = new PMDConfiguration();
        config.setInputPaths(sourceDir.toString());
        config.setRuleSets("category/java/bestpractices.xml");

        try (PmdAnalysis pmd = PmdAnalysis.create(config)) {
            RuleSetLoader ruleSetLoader = new RuleSetLoader();
            RuleSet ruleSet = ruleSetLoader.loadFromResource("category/java/bestpractices.xml");
            pmd.addRuleSet(ruleSet);

            Report report = pmd.performAnalysisAndCollectReport();

            // iterator cleaner than for loop here
            report.iterator().forEachRemaining(issue -> {
                issues.add(new Issue(
                    "PMD",
                    issue.getFilename(),
                    issue.getBeginLine(),
                    issue.getDescription(),
                    "Priority " + issue.getRule().getPriority().getPriority(),
                    issue.getRule().getName()
                ));
            });
        }

        return issues;
    }

    private static List<Issue> runCheckstyle(Path sourceDir) throws Exception {
        List<Issue> issues = new ArrayList<>();

        DefaultConfiguration config = new DefaultConfiguration("Checker");
        DefaultConfiguration treeWalker = new DefaultConfiguration("TreeWalker");

        treeWalker.addChild(new DefaultConfiguration("EmptyBlock"));
        treeWalker.addChild(new DefaultConfiguration("UnusedImports"));
        treeWalker.addChild(new DefaultConfiguration("JavadocType"));
        config.addChild(treeWalker);

        Checker checker = new Checker();
        checker.setModuleClassLoader(Checker.class.getClassLoader());
        checker.addListener(new AuditListener() {
            @Override public void auditStarted(AuditEvent event) {}
            @Override public void auditFinished(AuditEvent event) {}
            @Override public void fileStarted(AuditEvent event) {}
            @Override public void fileFinished(AuditEvent event) {}
            @Override public void addError(AuditEvent event) {
                issues.add(new Issue("Checkstyle", event.getFileName(), event.getLine(),
                    event.getMessage(), "Warning", "CheckstyleRule"));
            }
            @Override public void addException(AuditEvent event, Throwable throwable) {}
        });

        List<File> javaFiles = Files.walk(sourceDir)
                                .filter(p -> p.toString().endsWith(".java"))
                                .map(Path::toFile)
                                .collect(Collectors.toList());

        checker.configure(config);
        checker.process(javaFiles);
        checker.destroy();
        return issues;
    }

}
