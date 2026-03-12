package com.bonitasoft.connectors.gdrive;

import net.jqwik.api.*;
import net.jqwik.api.constraints.StringLength;
import org.bonitasoft.engine.connector.ConnectorValidationException;

import java.util.HashMap;
import java.util.Map;

class CreateFolderConnectorPropertyTest {

    @Property
    void mandatoryServiceAccountKeyRejectsBlank(@ForAll("blankStrings") String input) {
        CreateFolderConnector connector = new CreateFolderConnector();
        Map<String, Object> inputs = new HashMap<>();
        inputs.put(CreateFolderConnector.INPUT_SERVICE_ACCOUNT_KEY_JSON, input);
        inputs.put(CreateFolderConnector.INPUT_FOLDER_NAME, "ValidFolder");
        connector.setInputParameters(inputs);

        try {
            connector.validateInputParameters();
            throw new AssertionError("Expected ConnectorValidationException for blank serviceAccountKeyJson");
        } catch (ConnectorValidationException e) {
            // expected
        }
    }

    @Property
    void mandatoryFolderNameRejectsBlank(@ForAll("blankStrings") String input) {
        CreateFolderConnector connector = new CreateFolderConnector();
        Map<String, Object> inputs = new HashMap<>();
        inputs.put(CreateFolderConnector.INPUT_SERVICE_ACCOUNT_KEY_JSON, "{\"type\":\"service_account\"}");
        inputs.put(CreateFolderConnector.INPUT_FOLDER_NAME, input);
        connector.setInputParameters(inputs);

        try {
            connector.validateInputParameters();
            throw new AssertionError("Expected ConnectorValidationException for blank folderName");
        } catch (ConnectorValidationException e) {
            // expected
        }
    }

    @Property
    void missingServiceAccountKeyRejectsNull() {
        CreateFolderConnector connector = new CreateFolderConnector();
        Map<String, Object> inputs = new HashMap<>();
        inputs.put(CreateFolderConnector.INPUT_FOLDER_NAME, "ValidFolder");
        connector.setInputParameters(inputs);

        try {
            connector.validateInputParameters();
            throw new AssertionError("Expected ConnectorValidationException for null serviceAccountKeyJson");
        } catch (ConnectorValidationException e) {
            // expected
        }
    }

    @Property
    void missingFolderNameRejectsNull() {
        CreateFolderConnector connector = new CreateFolderConnector();
        Map<String, Object> inputs = new HashMap<>();
        inputs.put(CreateFolderConnector.INPUT_SERVICE_ACCOUNT_KEY_JSON, "{\"type\":\"service_account\"}");
        connector.setInputParameters(inputs);

        try {
            connector.validateInputParameters();
            throw new AssertionError("Expected ConnectorValidationException for null folderName");
        } catch (ConnectorValidationException e) {
            // expected
        }
    }

    @Property
    void validConfigurationAlwaysBuilds(
            @ForAll @StringLength(min = 1, max = 500) String serviceAccountKey,
            @ForAll @StringLength(min = 1, max = 200) String folderName
    ) {
        CreateFolderConnector connector = new CreateFolderConnector();
        Map<String, Object> inputs = new HashMap<>();
        inputs.put(CreateFolderConnector.INPUT_SERVICE_ACCOUNT_KEY_JSON, serviceAccountKey);
        inputs.put(CreateFolderConnector.INPUT_FOLDER_NAME, folderName);
        connector.setInputParameters(inputs);

        try {
            connector.validateInputParameters();
            // Should succeed for any non-blank strings
        } catch (ConnectorValidationException e) {
            // Only acceptable if the generated strings were blank
            if (!serviceAccountKey.isBlank() && !folderName.isBlank()) {
                throw new AssertionError("Unexpected validation failure for non-blank inputs", e);
            }
        }
    }

    @Property
    void optionalParentFolderIdAcceptsAnyString(
            @ForAll @StringLength(min = 0, max = 200) String parentFolderId
    ) {
        CreateFolderConnector connector = new CreateFolderConnector();
        Map<String, Object> inputs = new HashMap<>();
        inputs.put(CreateFolderConnector.INPUT_SERVICE_ACCOUNT_KEY_JSON, "{\"type\":\"service_account\"}");
        inputs.put(CreateFolderConnector.INPUT_FOLDER_NAME, "TestFolder");
        inputs.put(CreateFolderConnector.INPUT_PARENT_FOLDER_ID, parentFolderId);
        connector.setInputParameters(inputs);

        try {
            connector.validateInputParameters();
            // Optional fields should never cause validation failure
        } catch (ConnectorValidationException e) {
            throw new AssertionError("parentFolderId is optional and should not cause validation failure", e);
        }
    }

    @Property
    void optionalDescriptionAcceptsAnyString(
            @ForAll @StringLength(min = 0, max = 500) String description
    ) {
        CreateFolderConnector connector = new CreateFolderConnector();
        Map<String, Object> inputs = new HashMap<>();
        inputs.put(CreateFolderConnector.INPUT_SERVICE_ACCOUNT_KEY_JSON, "{\"type\":\"service_account\"}");
        inputs.put(CreateFolderConnector.INPUT_FOLDER_NAME, "TestFolder");
        inputs.put(CreateFolderConnector.INPUT_DESCRIPTION, description);
        connector.setInputParameters(inputs);

        try {
            connector.validateInputParameters();
        } catch (ConnectorValidationException e) {
            throw new AssertionError("description is optional and should not cause validation failure", e);
        }
    }

    @Property
    void defaultApplicationNameAppliedWhenNotProvided(
            @ForAll @StringLength(min = 1, max = 100) String folderName
    ) {
        CreateFolderConnector connector = new CreateFolderConnector();
        Map<String, Object> inputs = new HashMap<>();
        inputs.put(CreateFolderConnector.INPUT_SERVICE_ACCOUNT_KEY_JSON, "{\"type\":\"service_account\"}");
        inputs.put(CreateFolderConnector.INPUT_FOLDER_NAME, folderName);
        connector.setInputParameters(inputs);

        try {
            connector.validateInputParameters();
            if (!folderName.isBlank()) {
                var configField = AbstractGDriveConnector.class.getDeclaredField("configuration");
                configField.setAccessible(true);
                GDriveConfiguration config = (GDriveConfiguration) configField.get(connector);
                assert "Bonita-GoogleDrive-Connector".equals(config.getApplicationName())
                        : "Default application name should be Bonita-GoogleDrive-Connector";
            }
        } catch (ConnectorValidationException e) {
            if (!folderName.isBlank()) {
                throw new AssertionError("Should not fail for non-blank folderName", e);
            }
        } catch (Exception e) {
            throw new AssertionError("Unexpected exception during reflection", e);
        }
    }

    @Property
    void defaultTimeoutsAppliedWhenNotProvided(
            @ForAll @StringLength(min = 1, max = 100) String folderName
    ) {
        CreateFolderConnector connector = new CreateFolderConnector();
        Map<String, Object> inputs = new HashMap<>();
        inputs.put(CreateFolderConnector.INPUT_SERVICE_ACCOUNT_KEY_JSON, "{\"type\":\"service_account\"}");
        inputs.put(CreateFolderConnector.INPUT_FOLDER_NAME, folderName);
        connector.setInputParameters(inputs);

        try {
            connector.validateInputParameters();
            if (!folderName.isBlank()) {
                var configField = AbstractGDriveConnector.class.getDeclaredField("configuration");
                configField.setAccessible(true);
                GDriveConfiguration config = (GDriveConfiguration) configField.get(connector);
                assert config.getConnectTimeout() == 30000 : "Default connectTimeout should be 30000";
                assert config.getReadTimeout() == 60000 : "Default readTimeout should be 60000";
            }
        } catch (ConnectorValidationException e) {
            if (!folderName.isBlank()) {
                throw new AssertionError("Should not fail for non-blank folderName", e);
            }
        } catch (Exception e) {
            throw new AssertionError("Unexpected exception during reflection", e);
        }
    }

    @Property
    void customApplicationNameOverridesDefault(
            @ForAll @StringLength(min = 1, max = 100) String appName
    ) {
        CreateFolderConnector connector = new CreateFolderConnector();
        Map<String, Object> inputs = new HashMap<>();
        inputs.put(CreateFolderConnector.INPUT_SERVICE_ACCOUNT_KEY_JSON, "{\"type\":\"service_account\"}");
        inputs.put(CreateFolderConnector.INPUT_FOLDER_NAME, "TestFolder");
        inputs.put(CreateFolderConnector.INPUT_APPLICATION_NAME, appName);
        connector.setInputParameters(inputs);

        try {
            connector.validateInputParameters();
            var configField = AbstractGDriveConnector.class.getDeclaredField("configuration");
            configField.setAccessible(true);
            GDriveConfiguration config = (GDriveConfiguration) configField.get(connector);
            if (!appName.isBlank()) {
                assert appName.equals(config.getApplicationName())
                        : "Custom application name should override default";
            } else {
                assert "Bonita-GoogleDrive-Connector".equals(config.getApplicationName())
                        : "Blank application name should fall back to default";
            }
        } catch (ConnectorValidationException e) {
            throw new AssertionError("Should not fail validation", e);
        } catch (Exception e) {
            throw new AssertionError("Unexpected exception during reflection", e);
        }
    }

    @Property
    void folderNamePreservedInConfiguration(
            @ForAll @StringLength(min = 1, max = 200) String folderName
    ) {
        CreateFolderConnector connector = new CreateFolderConnector();
        Map<String, Object> inputs = new HashMap<>();
        inputs.put(CreateFolderConnector.INPUT_SERVICE_ACCOUNT_KEY_JSON, "{\"type\":\"service_account\"}");
        inputs.put(CreateFolderConnector.INPUT_FOLDER_NAME, folderName);
        connector.setInputParameters(inputs);

        try {
            connector.validateInputParameters();
            if (!folderName.isBlank()) {
                var configField = AbstractGDriveConnector.class.getDeclaredField("configuration");
                configField.setAccessible(true);
                GDriveConfiguration config = (GDriveConfiguration) configField.get(connector);
                assert folderName.equals(config.getFolderName())
                        : "Folder name should be preserved exactly in configuration";
            }
        } catch (ConnectorValidationException e) {
            if (!folderName.isBlank()) {
                throw new AssertionError("Should not fail for non-blank folderName", e);
            }
        } catch (Exception e) {
            throw new AssertionError("Unexpected exception during reflection", e);
        }
    }

    @Provide
    Arbitrary<String> blankStrings() {
        return Arbitraries.of("", " ", "\t", "\n", "  \t\n  ");
    }
}
