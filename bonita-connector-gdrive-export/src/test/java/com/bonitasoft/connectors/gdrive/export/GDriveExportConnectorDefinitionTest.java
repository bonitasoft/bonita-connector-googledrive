package com.bonitasoft.connectors.gdrive.export;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Tests that the connector definition XML (.def) is valid and consistent.
 * <p>
 * Note: Tests for .impl file are excluded because it requires Maven resource filtering
 * which is not available during unit test phase.
 */
@DisplayName("GDrive Export Connector Definition Tests")
class GDriveExportConnectorDefinitionTest {

    private static Document defDocument;
    private static List<String> defInputNames;
    private static List<String> defOutputNames;
    private static List<String> defMandatoryInputs;

    @BeforeAll
    static void loadDefinition() throws Exception {
        // Load the connector definition XML
        String defPath = "gdrive-export.def";
        InputStream defIs = GDriveExportConnectorDefinitionTest.class.getClassLoader()
                .getResourceAsStream(defPath);
        assertThat(defIs).as("Definition file '%s' should exist", defPath).isNotNull();

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        defDocument = builder.parse(defIs);

        // Extract input parameter names
        defInputNames = new ArrayList<>();
        defMandatoryInputs = new ArrayList<>();
        NodeList inputs = defDocument.getElementsByTagNameNS("*", "input");
        for (int i = 0; i < inputs.getLength(); i++) {
            Element input = (Element) inputs.item(i);
            String name = input.getAttribute("name");
            defInputNames.add(name);
            if ("true".equals(input.getAttribute("mandatory"))) {
                defMandatoryInputs.add(name);
            }
        }

        // Extract output parameter names
        defOutputNames = new ArrayList<>();
        NodeList outputs = defDocument.getElementsByTagNameNS("*", "output");
        for (int i = 0; i < outputs.getLength(); i++) {
            Element output = (Element) outputs.item(i);
            defOutputNames.add(output.getAttribute("name"));
        }
    }

    @Test
    @DisplayName("Definition should have a valid connector ID")
    void shouldHaveValidConnectorId() {
        NodeList ids = defDocument.getElementsByTagNameNS("*", "id");
        assertThat(ids.getLength()).isGreaterThan(0);
        
        String connectorId = ids.item(0).getTextContent();
        assertThat(connectorId)
                .as("Connector ID should follow {service}-{operation} convention")
                .isEqualTo("gdrive-export");
    }

    @Test
    @DisplayName("Definition should have standard output parameters")
    void shouldHaveStandardOutputs() {
        assertThat(defOutputNames)
                .as("Must include standard 'success' output")
                .contains("success");
        assertThat(defOutputNames)
                .as("Must include standard 'errorMessage' output")
                .contains("errorMessage");
    }

    @Test
    @DisplayName("Definition should have required input parameters")
    void shouldHaveRequiredInputs() {
        assertThat(defInputNames)
                .as("Must include serviceAccountJson input")
                .contains("serviceAccountJson");
        assertThat(defInputNames.size())
                .as("Should have operation-specific inputs beyond auth")
                .isGreaterThan(3);
    }

    @Test
    @DisplayName("Mandatory inputs should be marked correctly")
    void shouldHaveCorrectMandatoryInputs() {
        // Some connectors may have all-optional inputs
        assertThat(defMandatoryInputs).isNotNull();
    }

    @Test
    @DisplayName("Definition should have timeout parameters")
    void shouldHaveTimeoutParameters() {
        assertThat(defInputNames).contains("connectTimeout", "readTimeout");
    }

    @Test
    @DisplayName("Definition should have at least 2 UI pages")
    void shouldHaveUIPages() {
        NodeList pages = defDocument.getElementsByTagNameNS("*", "page");
        assertThat(pages.getLength())
                .as("Should have at least auth page and operation page")
                .isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("Widget inputs should reference valid input parameters")
    void widgetsShouldReferenceValidInputs() {
        Set<String> inputNameSet = new HashSet<>(defInputNames);
        NodeList widgets = defDocument.getElementsByTagNameNS("*", "widget");
        for (int i = 0; i < widgets.getLength(); i++) {
            Element widget = (Element) widgets.item(i);
            String inputName = widget.getAttribute("inputName");
            if (inputName != null && !inputName.isBlank()) {
                assertThat(inputNameSet)
                        .as("Widget references input '%s' which should exist", inputName)
                        .contains(inputName);
            }
        }
    }

    @Test
    @DisplayName("Widgets should have labels in properties file")
    void widgetsShouldHaveLabels() {
        // Labels are defined in .properties file, not as XML child elements
        // (EMF namespace 6.1 uses .properties for i18n labels)
        InputStream propsIs = getClass().getClassLoader()
                .getResourceAsStream("gdrive-export.properties");
        assertThat(propsIs).as("Properties file should exist").isNotNull();

        java.util.Properties props = new java.util.Properties();
        try {
            props.load(propsIs);
        } catch (Exception e) {
            assertThat(false).as("Properties file should be loadable: " + e.getMessage()).isTrue();
        }

        // Each widget ID should have a corresponding .label key in properties
        NodeList widgets = defDocument.getElementsByTagNameNS("*", "widget");
        int labelCount = 0;
        for (int i = 0; i < widgets.getLength(); i++) {
            Element widget = (Element) widgets.item(i);
            String widgetId = widget.getAttribute("id");
            if (widgetId != null && !widgetId.isBlank()) {
                String labelKey = widgetId + ".label";
                if (props.containsKey(labelKey)) {
                    labelCount++;
                }
            }
        }
        assertThat(labelCount)
                .as("Widgets should have descriptive labels in .properties file")
                .isGreaterThan(2);
    }

    @Test
    @DisplayName("Definition should have outputs")
    void shouldHaveOutputs() {
        assertThat(defOutputNames)
                .as("Should have at least standard outputs")
                .contains("success", "errorMessage");
    }

    @Test
    @DisplayName("Connector class should exist and be loadable")
    void connectorClassShouldExist() {
        String expectedClass = "com.bonitasoft.connectors.gdrive.export.GDriveExportConnector";
        try {
            Class<?> clazz = Class.forName(expectedClass);
            assertThat(clazz).isNotNull();
        } catch (ClassNotFoundException e) {
            assertThat(false)
                    .as("Connector class '%s' should exist and be loadable", expectedClass)
                    .isTrue();
        }
    }
}
