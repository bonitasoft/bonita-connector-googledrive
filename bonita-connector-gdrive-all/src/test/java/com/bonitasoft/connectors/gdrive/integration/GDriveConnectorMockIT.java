package com.bonitasoft.connectors.gdrive.integration;

import com.bonitasoft.connectors.gdrive.AbstractGDriveConnector;
import com.bonitasoft.connectors.gdrive.GDriveClient;
import com.bonitasoft.connectors.gdrive.GDriveException;
import com.bonitasoft.connectors.gdrive.copy.GDriveCopyConnector;
import com.bonitasoft.connectors.gdrive.createfolder.GDriveCreateFolderConnector;
import com.bonitasoft.connectors.gdrive.delete.GDriveDeleteConnector;
import com.bonitasoft.connectors.gdrive.download.GDriveDownloadConnector;
import com.bonitasoft.connectors.gdrive.export.GDriveExportConnector;
import com.bonitasoft.connectors.gdrive.move.GDriveMoveConnector;
import com.bonitasoft.connectors.gdrive.search.GDriveSearchConnector;
import com.bonitasoft.connectors.gdrive.upload.GDriveUploadConnector;

import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;

import org.bonitasoft.engine.connector.ConnectorException;
import org.bonitasoft.engine.connector.ConnectorValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.SocketTimeoutException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Nivel 2 — Mock Integration Tests for Google Drive connectors.
 * <p>
 * Mocks the GDriveClient (Google Drive API) with Mockito to test the full
 * connector lifecycle (VALIDATE -> EXECUTE -> outputs) without real credentials.
 */
@Tag("integration")
@DisplayName("Nivel 2 — Google Drive Connector Mock Integration Tests")
@ExtendWith(MockitoExtension.class)
class GDriveConnectorMockIT {

    @Mock
    private GDriveClient mockClient;

    @Mock
    private Drive mockDriveService;

    private Map<String, Object> authParams() {
        Map<String, Object> params = new HashMap<>();
        params.put("serviceAccountJson", "{\"type\": \"service_account\", \"project_id\": \"test\"}");
        return params;
    }

    /**
     * Inject a mock GDriveClient into a connector using the protected setClient method.
     */
    private void injectClient(AbstractGDriveConnector connector, GDriveClient client) throws Exception {
        var field = AbstractGDriveConnector.class.getDeclaredField("client");
        field.setAccessible(true);
        field.set(connector, client);
    }

    /**
     * Execute the connector's business logic via reflection (protected method).
     */
    private void executeLogic(AbstractGDriveConnector connector) throws Exception {
        var method = AbstractGDriveConnector.class.getDeclaredMethod("executeBusinessLogic");
        method.setAccessible(true);
        try {
            method.invoke(connector);
        } catch (java.lang.reflect.InvocationTargetException e) {
            if (e.getCause() instanceof ConnectorException ce) throw ce;
            if (e.getCause() instanceof RuntimeException re) throw re;
            throw e;
        }
    }

    // ---- Test 1: Instantiation of all 8 connectors ----

    @Test
    @DisplayName("should_instantiate_all_8_connectors")
    void should_instantiate_all_8_connectors() {
        AbstractGDriveConnector[] connectors = {
                new GDriveUploadConnector(),
                new GDriveDownloadConnector(),
                new GDriveExportConnector(),
                new GDriveCreateFolderConnector(),
                new GDriveDeleteConnector(),
                new GDriveMoveConnector(),
                new GDriveCopyConnector(),
                new GDriveSearchConnector()
        };

        assertThat(connectors).hasSize(8);
        for (var connector : connectors) {
            assertThat(connector).isNotNull();
        }
    }

    // ---- Test 2: Validate upload with valid inputs ----

    @Test
    @DisplayName("should_validate_upload_with_valid_inputs")
    void should_validate_upload_with_valid_inputs() throws ConnectorValidationException {
        var connector = new GDriveUploadConnector();
        Map<String, Object> params = authParams();
        params.put("fileName", "test-report.pdf");
        params.put("fileContent", Base64.getEncoder().encodeToString("test content".getBytes()));

        connector.setInputParameters(params);
        connector.validateInputParameters(); // Should not throw
    }

    // ---- Test 3: Set standard outputs (success, errorMessage) ----

    @Test
    @DisplayName("should_set_standard_outputs")
    void should_set_standard_outputs() throws Exception {
        var connector = new GDriveUploadConnector();
        Map<String, Object> params = authParams();
        params.put("fileName", "test.pdf");
        params.put("fileContent", Base64.getEncoder().encodeToString("data".getBytes()));
        params.put("mimeType", "application/pdf");
        connector.setInputParameters(params);

        File uploadedFile = new File();
        uploadedFile.setId("file-abc");
        uploadedFile.setName("test.pdf");
        uploadedFile.setMimeType("application/pdf");
        uploadedFile.setSize(4L);
        uploadedFile.setWebViewLink("https://drive.google.com/file/d/file-abc/view");
        uploadedFile.setWebContentLink("https://drive.google.com/uc?id=file-abc");
        uploadedFile.setMd5Checksum("md5hash");

        when(mockClient.getDriveService()).thenReturn(mockDriveService);
        when(mockClient.executeWithRetry(any(Callable.class), anyString()))
                .thenReturn(uploadedFile);

        injectClient(connector, mockClient);
        executeLogic(connector);

        Map<String, Object> outputs = connector.getOutputs();
        assertThat(outputs.get("success")).isEqualTo(true);
        assertThat(outputs.get("errorMessage")).isEqualTo("");
        assertThat(outputs.get("fileId")).isEqualTo("file-abc");
        assertThat(outputs.get("fileName")).isEqualTo("test.pdf");
        assertThat(outputs.get("mimeType")).isEqualTo("application/pdf");
    }

    // ---- Test 4: Handle API error ----

    @Test
    @DisplayName("should_handle_api_error")
    void should_handle_api_error() throws Exception {
        var connector = new GDriveUploadConnector();
        Map<String, Object> params = authParams();
        params.put("fileName", "test.pdf");
        params.put("fileContent", Base64.getEncoder().encodeToString("data".getBytes()));
        connector.setInputParameters(params);

        when(mockClient.getDriveService()).thenReturn(mockDriveService);
        when(mockClient.executeWithRetry(any(Callable.class), anyString()))
                .thenThrow(new GDriveException("Permission denied: insufficient scope",
                        403, "insufficientPermissions", false, null));

        injectClient(connector, mockClient);
        assertThatThrownBy(() -> executeLogic(connector))
                .isInstanceOf(ConnectorException.class);

        Map<String, Object> outputs = connector.getOutputs();
        assertThat(outputs.get("success")).isEqualTo(false);
        assertThat((String) outputs.get("errorMessage")).contains("Permission denied");
    }

    // ---- Test 5: Handle timeout ----

    @Test
    @DisplayName("should_handle_timeout")
    void should_handle_timeout() throws Exception {
        var connector = new GDriveUploadConnector();
        Map<String, Object> params = authParams();
        params.put("fileName", "test.pdf");
        params.put("fileContent", Base64.getEncoder().encodeToString("data".getBytes()));
        connector.setInputParameters(params);

        when(mockClient.getDriveService()).thenReturn(mockDriveService);
        when(mockClient.executeWithRetry(any(Callable.class), anyString()))
                .thenThrow(new GDriveException("Unexpected error: Read timed out",
                        new SocketTimeoutException("Read timed out")));

        injectClient(connector, mockClient);
        assertThatThrownBy(() -> executeLogic(connector))
                .isInstanceOf(ConnectorException.class);

        Map<String, Object> outputs = connector.getOutputs();
        assertThat(outputs.get("success")).isEqualTo(false);
        assertThat((String) outputs.get("errorMessage")).contains("timed out");
    }

    // ---- Test 6: Handle null response ----

    @Test
    @DisplayName("should_handle_null_response")
    void should_handle_null_response() throws Exception {
        var connector = new GDriveUploadConnector();
        Map<String, Object> params = authParams();
        params.put("fileName", "test.pdf");
        params.put("fileContent", Base64.getEncoder().encodeToString("data".getBytes()));
        connector.setInputParameters(params);

        when(mockClient.getDriveService()).thenReturn(mockDriveService);
        when(mockClient.executeWithRetry(any(Callable.class), anyString()))
                .thenReturn(null);

        injectClient(connector, mockClient);
        assertThatThrownBy(() -> executeLogic(connector))
                .isInstanceOf(ConnectorException.class);

        Map<String, Object> outputs = connector.getOutputs();
        assertThat(outputs.get("success")).isEqualTo(false);
    }

    // ---- Test 7: Validate service account JSON required ----

    @Test
    @DisplayName("should_validate_service_account_json_required")
    void should_validate_service_account_json_required() {
        var connector = new GDriveUploadConnector();
        Map<String, Object> params = new HashMap<>();
        params.put("fileName", "test.pdf");
        params.put("fileContent", Base64.getEncoder().encodeToString("data".getBytes()));
        // No auth params

        connector.setInputParameters(params);

        assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("Authentication required");
    }

    // ---- Test 8: Reject file path instead of JSON ----

    @Test
    @DisplayName("should_reject_file_path_instead_of_json")
    void should_reject_file_path_instead_of_json() {
        var connector = new GDriveUploadConnector();
        Map<String, Object> params = new HashMap<>();
        params.put("serviceAccountJson", "/path/to/service-account.json");
        params.put("fileName", "test.pdf");
        params.put("fileContent", Base64.getEncoder().encodeToString("data".getBytes()));

        connector.setInputParameters(params);

        assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("JSON content");
    }
}
