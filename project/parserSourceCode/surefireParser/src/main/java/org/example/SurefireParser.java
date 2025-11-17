package org.example;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

public class SurefireParser {

    public enum Status {
        PASSED,
        FAILED,
        ERROR,
        SKIPPED
    }

    public static final class TestSummary {
        private final String testName;
        private final Status status;
        private final String failureType;
        private final String failureMessage;

        public TestSummary(String testName, Status status, String failureType, String failureMessage) {
            this.testName = testName;
            this.status = status;
            this.failureType = failureType;
            this.failureMessage = failureMessage;
        }

        public String getTestName() { return testName; }
        public Status getStatus() { return status; }
        public String getFailureType() { return failureType; }
        public String getFailureMessage() { return failureMessage; }
    }

    public static final class SurefireResult {
        private final Map<String, List<TestSummary>> classTestResults;

        public SurefireResult(Map<String, List<TestSummary>> classTestResults) {
            this.classTestResults = classTestResults;
        }

        public Map<String, List<TestSummary>> getClassTestResults() {
            return classTestResults;
        }
    }

    public static SurefireResult parse(String xmlFilePath) throws Exception {
        File xmlFile = new File(xmlFilePath);
        if (!xmlFile.isFile()) {
            throw new IllegalArgumentException("surefire report not found: " + xmlFilePath);
        }

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setEntityResolver((publicId, systemId) -> new InputSource(new java.io.StringReader("")));
        Document doc = builder.parse(xmlFile);

        NodeList testcases = doc.getElementsByTagName("testcase");
        Map<String, List<TestSummary>> results = new LinkedHashMap<>();

        for (int i = 0; i < testcases.getLength(); i++) {
            Node tcNode = testcases.item(i);
            if (!(tcNode instanceof Element)) continue;
            Element tc = (Element) tcNode;

            String className = tc.getAttribute("classname");
            String testName = tc.getAttribute("name");

            Status status = Status.PASSED;
            String failureType = null;
            String failureMessage = null;

            NodeList children = tc.getChildNodes();
            for (int c = 0; c < children.getLength(); c++) {
                Node child = children.item(c);
                if (!(child instanceof Element)) continue;
                String nodeName = child.getNodeName();
                if ("failure".equals(nodeName)) {
                    status = Status.FAILED;
                    Element e = (Element) child;
                    failureType = e.getAttribute("type");
                    failureMessage = e.getAttribute("message");
                    break;
                } else if ("error".equals(nodeName)) {
                    status = Status.ERROR;
                    Element e = (Element) child;
                    failureType = e.getAttribute("type");
                    failureMessage = e.getAttribute("message");
                    break;
                } else if ("skipped".equals(nodeName)) {
                    status = Status.SKIPPED;
                    Element e = (Element) child;
                    failureMessage = e.getAttribute("message");
                    break;
                }
            }

            if (status == Status.FAILED || status == Status.ERROR) {
                if (className == null) className = "";
                results.computeIfAbsent(className, k -> new ArrayList<>())
                       .add(new TestSummary(testName, status, failureType, failureMessage));
            }
        }

        return new SurefireResult(results);
    }

    public static SurefireResult parseDirectory(String dirPath) throws Exception {
        File dir = new File(dirPath);
        if (!dir.isDirectory()) {
            throw new IllegalArgumentException("Directory not found: " + dirPath);
        }

        File[] files = dir.listFiles((d, name) -> name.endsWith(".xml"));
        if (files == null) files = new File[0];

        Map<String, List<TestSummary>> aggregated = new LinkedHashMap<>();
        for (File f : files) {
            if (!f.isFile()) continue;
            SurefireResult r = parse(f.getAbsolutePath());
            for (Map.Entry<String, List<TestSummary>> e : r.getClassTestResults().entrySet()) {
                aggregated.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).addAll(e.getValue());
            }
        }

        return new SurefireResult(aggregated);
    }
}

