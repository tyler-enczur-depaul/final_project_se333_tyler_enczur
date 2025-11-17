package org.example;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

public class JacocoParser {

    public static final class LineCoverage {
        private final int lineNumber;
        private final int missedInstructions;
        private final int coveredInstructions;
        private final int missedBranches;
        private final int coveredBranches;

        public LineCoverage(int lineNumber, int missedInstructions, int coveredInstructions,
                            int missedBranches, int coveredBranches) {
            this.lineNumber = lineNumber;
            this.missedInstructions = missedInstructions;
            this.coveredInstructions = coveredInstructions;
            this.missedBranches = missedBranches;
            this.coveredBranches = coveredBranches;
        }

        public int getLineNumber() { return lineNumber; }
        public int getMissedInstructions() { return missedInstructions; }
        public int getCoveredInstructions() { return coveredInstructions; }
        public int getMissedBranches() { return missedBranches; }
        public int getCoveredBranches() { return coveredBranches; }
    }

    public static final class JacocoResult {
        private final Map<String, List<LineCoverage>> fileCoverages;

        public JacocoResult(Map<String, List<LineCoverage>> fileCoverages) {
            this.fileCoverages = fileCoverages;
        }

        public Map<String, List<LineCoverage>> getFileCoverages() { return fileCoverages; }
    }

    public static JacocoResult parse(String xmlFilePath) throws Exception {
        File xmlFile = new File(xmlFilePath);
        if (!xmlFile.exists()) {
            throw new RuntimeException("Jacoco report not found at: " + xmlFilePath);
        }

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();

        // prevents weird errors during parsing
        builder.setEntityResolver((publicId, systemId) -> new InputSource(new java.io.StringReader("")));
        Document document = builder.parse(xmlFile);
        XPath xpath = XPathFactory.newInstance().newXPath();

        Map<String, List<LineCoverage>> classCoverages = new HashMap<>();
        NodeList packageNodes = (NodeList) xpath.evaluate("//package", document, XPathConstants.NODESET);

        for (int i = 0; i < packageNodes.getLength(); ++i) {
            Element pkgElem = (Element) packageNodes.item(i);
            String pkgName = pkgElem.getAttribute("name").replace('/', '.');

            NodeList sourcefileNodes = pkgElem.getElementsByTagName("sourcefile");
            for (int j = 0; j < sourcefileNodes.getLength(); ++j) {
                Element sfElem = (Element) sourcefileNodes.item(j);
                String fileName = sfElem.getAttribute("name");
                int dotIdx = fileName.lastIndexOf('.');
                String baseName = (dotIdx > 0) ? fileName.substring(0, dotIdx) : fileName;
                String fullyQualifiedClassName = pkgName.isEmpty() ? baseName : pkgName + "." + baseName;

                NodeList lineNodes = sfElem.getElementsByTagName("line");
                List<LineCoverage> lines = new ArrayList<>();

                for (int k = 0; k < lineNodes.getLength(); ++k) {
                    Element lElem = (Element) lineNodes.item(k);
                    int nr = Integer.parseInt(lElem.getAttribute("nr"));
                    int mi = Integer.parseInt(lElem.getAttribute("mi"));
                    int ci = Integer.parseInt(lElem.getAttribute("ci"));
                    int mb = Integer.parseInt(lElem.getAttribute("mb"));
                    int cb = Integer.parseInt(lElem.getAttribute("cb"));

                    lines.add(new LineCoverage(nr, mi, ci, mb, cb));
                }

                classCoverages.put(fullyQualifiedClassName, lines);
            }
        }

        return new JacocoResult(classCoverages);
    }
}

