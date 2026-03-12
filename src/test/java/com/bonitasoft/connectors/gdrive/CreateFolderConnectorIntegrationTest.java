package com.bonitasoft.connectors.gdrive;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.Map;

@EnabledIfEnvironmentVariable(named = "GDRIVE_SERVICE_ACCOUNT_KEY_JSON", matches = ".+")
@EnabledIfEnvironmentVariable(named = "GDRIVE_IMPERSONATED_USER_EMAIL", matches = ".+")
class CreateFolderConnectorIntegrationTest {

    private GDriveClient client;

    @BeforeEach
    void setUp() throws Exception {
        GDriveConfiguration config = GDriveConfiguration.builder()
                .serviceAccountKeyJson(System.getenv("GDRIVE_SERVICE_ACCOUNT_KEY_JSON"))
                .impersonatedUserEmail(System.getenv("GDRIVE_IMPERSONATED_USER_EMAIL"))
                .build();
        client = new GDriveClient(config);
    }

    private void cleanupFile(String fileId) {
        try { client.deleteFile(fileId, true); } catch (Exception ignored) {}
    }

    @Test
    void shouldCreateFolderInRootDrive() throws Exception {
        CreateFolderConnector connector = new CreateFolderConnector();
        connector.setInputParameters(Map.of(
                CreateFolderConnector.INPUT_SERVICE_ACCOUNT_KEY_JSON, System.getenv("GDRIVE_SERVICE_ACCOUNT_KEY_JSON"),
                CreateFolderConnector.INPUT_IMPERSONATED_USER_EMAIL, System.getenv("GDRIVE_IMPERSONATED_USER_EMAIL"),
                CreateFolderConnector.INPUT_FOLDER_NAME, "IntegrationTest-CreateFolder-" + System.currentTimeMillis(),
                CreateFolderConnector.INPUT_DESCRIPTION, "Created by integration test"
        ));
        connector.validateInputParameters();
        connector.connect();
        connector.executeBusinessLogic();
        connector.disconnect();

        Map<String, Object> outputs = connector.getOutputs();
        assertThat(outputs.get(AbstractGDriveConnector.OUTPUT_SUCCESS)).isEqualTo(true);
        String folderId = (String) outputs.get(CreateFolderConnector.OUTPUT_FOLDER_ID);
        assertThat(folderId).isNotBlank();
        assertThat((String) outputs.get(CreateFolderConnector.OUTPUT_FOLDER_WEB_VIEW_LINK)).startsWith("https://");

        cleanupFile(folderId);
    }

    @Test
    void shouldCreateSubFolderInParent() throws Exception {
        GDriveClient.CreateFolderResult parent = client.createFolder(
                "IntegrationTest-Parent-" + System.currentTimeMillis(), null, null);
        try {
            CreateFolderConnector connector = new CreateFolderConnector();
            connector.setInputParameters(Map.of(
                    CreateFolderConnector.INPUT_SERVICE_ACCOUNT_KEY_JSON, System.getenv("GDRIVE_SERVICE_ACCOUNT_KEY_JSON"),
                    CreateFolderConnector.INPUT_IMPERSONATED_USER_EMAIL, System.getenv("GDRIVE_IMPERSONATED_USER_EMAIL"),
                    CreateFolderConnector.INPUT_FOLDER_NAME, "IntegrationTest-SubFolder-" + System.currentTimeMillis(),
                    CreateFolderConnector.INPUT_PARENT_FOLDER_ID, parent.folderId()
            ));
            connector.validateInputParameters();
            connector.connect();
            connector.executeBusinessLogic();
            connector.disconnect();

            assertThat(connector.getOutputs().get(AbstractGDriveConnector.OUTPUT_SUCCESS)).isEqualTo(true);
            assertThat(connector.getOutputs().get(CreateFolderConnector.OUTPUT_FOLDER_ID)).isNotNull();
        } finally {
            cleanupFile(parent.folderId());
        }
    }

    @Test
    void shouldFailWithInvalidCredentials() throws Exception {
        CreateFolderConnector connector = new CreateFolderConnector();
        connector.setInputParameters(Map.of(
                CreateFolderConnector.INPUT_SERVICE_ACCOUNT_KEY_JSON, "{\"type\":\"service_account\",\"project_id\":\"fake\"}",
                CreateFolderConnector.INPUT_FOLDER_NAME, "ShouldNotBeCreated"
        ));
        connector.validateInputParameters();

        assertThatThrownBy(connector::connect)
                .isInstanceOf(org.bonitasoft.engine.connector.ConnectorException.class);
    }
}
