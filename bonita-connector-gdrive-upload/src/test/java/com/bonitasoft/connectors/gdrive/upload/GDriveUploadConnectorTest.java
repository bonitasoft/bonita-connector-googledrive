package com.bonitasoft.connectors.gdrive.upload;

import com.bonitasoft.connectors.gdrive.GDriveClient;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import org.bonitasoft.engine.connector.ConnectorException;
import org.bonitasoft.engine.connector.ConnectorValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@DisplayName("GDriveUploadConnector")
class GDriveUploadConnectorTest {

    private GDriveUploadConnector connector;

    @BeforeEach
    void setUp() {
        connector = new GDriveUploadConnector();
    }

    @Nested
    @DisplayName("Validation")
    class Validation {

        @Test
        @DisplayName("should fail when serviceAccountJson is missing")
        void shouldFailOnMissingAuth() {
            Map<String, Object> params = new HashMap<>();
            params.put("fileName", "test.pdf");
            params.put("fileContent", Base64.getEncoder().encodeToString("test".getBytes()));

            connector.setInputParameters(params);

            assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("Authentication required");
        }

        @Test
        @DisplayName("should fail when fileName is missing")
        void shouldFailOnMissingFileName() {
            Map<String, Object> params = new HashMap<>();
            params.put("serviceAccountJson", "{\"type\": \"service_account\"}");
            params.put("fileContent", Base64.getEncoder().encodeToString("test".getBytes()));

            connector.setInputParameters(params);

            assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("fileName");
        }

        @Test
        @DisplayName("should fail when fileContent is invalid Base64")
        void shouldFailOnInvalidBase64() {
            Map<String, Object> params = new HashMap<>();
            params.put("serviceAccountJson", "{\"type\": \"service_account\"}");
            params.put("fileName", "test.pdf");
            params.put("fileContent", "not-valid-base64!!!");

            connector.setInputParameters(params);

            assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("Base64");
        }

        @Test
        @DisplayName("should fail when fileName exceeds max length")
        void shouldFailOnFileNameTooLong() {
            Map<String, Object> params = new HashMap<>();
            params.put("serviceAccountJson", "{\"type\": \"service_account\"}");
            params.put("fileName", "a".repeat(500));
            params.put("fileContent", Base64.getEncoder().encodeToString("test".getBytes()));
            connector.setInputParameters(params);
            assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("fileName exceeds maximum length");
        }

        @Test
        @DisplayName("should fail when fileContent exceeds max length")
        void shouldFailOnFileContentTooLong() {
            Map<String, Object> params = new HashMap<>();
            params.put("serviceAccountJson", "{\"type\": \"service_account\"}");
            params.put("fileName", "test.pdf");
            // Create a string longer than MAX_FILE_CONTENT_LENGTH (50_000_000)
            // without allocating huge memory - just repeat a valid base64 char
            params.put("fileContent", "A".repeat(50_000_001));
            connector.setInputParameters(params);
            assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("fileContent exceeds maximum length");
        }

        @Test
        @DisplayName("should fail when fileContent is blank")
        void shouldFailOnBlankFileContent() {
            Map<String, Object> params = new HashMap<>();
            params.put("serviceAccountJson", "{\"type\": \"service_account\"}");
            params.put("fileName", "test.pdf");
            params.put("fileContent", "   ");
            connector.setInputParameters(params);
            assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("fileContent");
        }

        @Test
        @DisplayName("should fail when description exceeds max length")
        void shouldFailOnDescriptionTooLong() {
            Map<String, Object> params = new HashMap<>();
            params.put("serviceAccountJson", "{\"type\": \"service_account\"}");
            params.put("fileName", "test.pdf");
            params.put("fileContent", Base64.getEncoder().encodeToString("test".getBytes()));
            params.put("description", "a".repeat(5001));
            connector.setInputParameters(params);
            assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("description exceeds maximum length");
        }

        @Test
        @DisplayName("should pass with all required parameters")
        void shouldPassWithRequiredParams() throws ConnectorValidationException {
            Map<String, Object> params = new HashMap<>();
            params.put("serviceAccountJson", "{\"type\": \"service_account\"}");
            params.put("fileName", "test.pdf");
            params.put("fileContent", Base64.getEncoder().encodeToString("test".getBytes()));

            connector.setInputParameters(params);

            connector.validateInputParameters(); // Should not throw
        }
    }

    @Nested
    @DisplayName("Connector metadata")
    class Metadata {

        @Test
        @DisplayName("should return correct connector name")
        void shouldReturnConnectorName() {
            assertThat(connector.getConnectorName()).isEqualTo("GDrive-Upload");
        }
    }

    @Nested
    @DisplayName("Execution")
    @ExtendWith(MockitoExtension.class)
    class Execution {

        @Mock
        private GDriveClient mockClient;

        @Mock
        private Drive mockDriveService;

        private void injectClient(GDriveUploadConnector c, GDriveClient client) throws Exception {
            var field = com.bonitasoft.connectors.gdrive.AbstractGDriveConnector.class.getDeclaredField("client");
            field.setAccessible(true);
            field.set(c, client);
        }

        private void executeLogic(GDriveUploadConnector c) throws Exception {
            var method = com.bonitasoft.connectors.gdrive.AbstractGDriveConnector.class.getDeclaredMethod("executeBusinessLogic");
            method.setAccessible(true);
            try {
                method.invoke(c);
            } catch (java.lang.reflect.InvocationTargetException e) {
                if (e.getCause() instanceof ConnectorException ce) throw ce;
                if (e.getCause() instanceof RuntimeException re) throw re;
                throw e;
            }
        }

        @Test
        @DisplayName("should_upload_file_and_set_all_outputs_when_all_inputs_provided")
        void should_upload_file_and_set_all_outputs_when_all_inputs_provided() throws Exception {
            Map<String, Object> params = new HashMap<>();
            params.put("serviceAccountJson", "{\"type\": \"service_account\"}");
            params.put("fileName", "report.pdf");
            params.put("fileContent", Base64.getEncoder().encodeToString("test content".getBytes()));
            params.put("mimeType", "application/pdf");
            params.put("parentFolderId", "folder-123");
            params.put("description", "A test report");
            params.put("customProperties", Map.of("project", "bonita"));

            connector.setInputParameters(params);
            connector.validateInputParameters();

            File uploadedFile = new File();
            uploadedFile.setId("file-456");
            uploadedFile.setName("report.pdf");
            uploadedFile.setMimeType("application/pdf");
            uploadedFile.setSize(1024L);
            uploadedFile.setMd5Checksum("abc123");
            uploadedFile.setWebViewLink("https://drive.google.com/file/d/file-456/view");
            uploadedFile.setWebContentLink("https://drive.google.com/uc?id=file-456");

            when(mockClient.getDriveService()).thenReturn(mockDriveService);
            when(mockClient.executeWithRetry(any(Callable.class), anyString()))
                    .thenReturn(uploadedFile);

            injectClient(connector, mockClient);

            executeLogic(connector);

            Map<String, Object> outputs = connector.getOutputs();
            assertThat(outputs.get("fileId")).isEqualTo("file-456");
            assertThat(outputs.get("fileName")).isEqualTo("report.pdf");
            assertThat(outputs.get("mimeType")).isEqualTo("application/pdf");
            assertThat(outputs.get("size")).isEqualTo(1024L);
            assertThat(outputs.get("md5Checksum")).isEqualTo("abc123");
            assertThat(outputs.get("webViewLink")).isEqualTo("https://drive.google.com/file/d/file-456/view");
            assertThat(outputs.get("webContentLink")).isEqualTo("https://drive.google.com/uc?id=file-456");
            assertThat(outputs.get("success")).isEqualTo(true);
        }

        @Test
        @DisplayName("should_upload_with_minimal_inputs_when_only_required_params")
        void should_upload_with_minimal_inputs_when_only_required_params() throws Exception {
            Map<String, Object> params = new HashMap<>();
            params.put("serviceAccountJson", "{\"type\": \"service_account\"}");
            params.put("fileName", "simple.txt");
            params.put("fileContent", Base64.getEncoder().encodeToString("hello".getBytes()));

            connector.setInputParameters(params);
            connector.validateInputParameters();

            File uploadedFile = new File();
            uploadedFile.setId("file-minimal");
            uploadedFile.setName("simple.txt");
            uploadedFile.setMimeType("application/octet-stream");

            when(mockClient.getDriveService()).thenReturn(mockDriveService);
            when(mockClient.executeWithRetry(any(Callable.class), anyString()))
                    .thenReturn(uploadedFile);

            injectClient(connector, mockClient);

            executeLogic(connector);

            Map<String, Object> outputs = connector.getOutputs();
            assertThat(outputs.get("fileId")).isEqualTo("file-minimal");
            assertThat(outputs.get("success")).isEqualTo(true);
        }

        @Test
        @DisplayName("should_convert_to_google_format_when_enabled_with_docx")
        void should_convert_to_google_format_when_enabled_with_docx() throws Exception {
            Map<String, Object> params = new HashMap<>();
            params.put("serviceAccountJson", "{\"type\": \"service_account\"}");
            params.put("fileName", "doc.docx");
            params.put("fileContent", Base64.getEncoder().encodeToString("content".getBytes()));
            params.put("mimeType", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            params.put("convertToGoogleFormat", true);

            connector.setInputParameters(params);
            connector.validateInputParameters();

            File uploadedFile = new File();
            uploadedFile.setId("file-converted");
            uploadedFile.setName("doc.docx");
            uploadedFile.setMimeType("application/vnd.google-apps.document");

            when(mockClient.getDriveService()).thenReturn(mockDriveService);
            when(mockClient.executeWithRetry(any(Callable.class), anyString()))
                    .thenReturn(uploadedFile);

            injectClient(connector, mockClient);

            executeLogic(connector);

            Map<String, Object> outputs = connector.getOutputs();
            assertThat(outputs.get("mimeType")).isEqualTo("application/vnd.google-apps.document");
            assertThat(outputs.get("success")).isEqualTo(true);
        }

        @Test
        @DisplayName("should_not_convert_when_mimeType_has_no_google_equivalent")
        void should_not_convert_when_mimeType_has_no_google_equivalent() throws Exception {
            Map<String, Object> params = new HashMap<>();
            params.put("serviceAccountJson", "{\"type\": \"service_account\"}");
            params.put("fileName", "image.png");
            params.put("fileContent", Base64.getEncoder().encodeToString("png-data".getBytes()));
            params.put("mimeType", "image/png");
            params.put("convertToGoogleFormat", true);

            connector.setInputParameters(params);
            connector.validateInputParameters();

            File uploadedFile = new File();
            uploadedFile.setId("file-png");
            uploadedFile.setName("image.png");
            uploadedFile.setMimeType("image/png");

            when(mockClient.getDriveService()).thenReturn(mockDriveService);
            when(mockClient.executeWithRetry(any(Callable.class), anyString()))
                    .thenReturn(uploadedFile);

            injectClient(connector, mockClient);
            executeLogic(connector);

            Map<String, Object> outputs = connector.getOutputs();
            assertThat(outputs.get("mimeType")).isEqualTo("image/png");
            assertThat(outputs.get("success")).isEqualTo(true);
        }

        @Test
        @DisplayName("should_not_convert_when_convertToGoogleFormat_is_false")
        void should_not_convert_when_convertToGoogleFormat_is_false() throws Exception {
            Map<String, Object> params = new HashMap<>();
            params.put("serviceAccountJson", "{\"type\": \"service_account\"}");
            params.put("fileName", "doc.docx");
            params.put("fileContent", Base64.getEncoder().encodeToString("docx".getBytes()));
            params.put("mimeType", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            params.put("convertToGoogleFormat", false);

            connector.setInputParameters(params);
            connector.validateInputParameters();

            File uploadedFile = new File();
            uploadedFile.setId("file-docx");
            uploadedFile.setName("doc.docx");
            uploadedFile.setMimeType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");

            when(mockClient.getDriveService()).thenReturn(mockDriveService);
            when(mockClient.executeWithRetry(any(Callable.class), anyString()))
                    .thenReturn(uploadedFile);

            injectClient(connector, mockClient);
            executeLogic(connector);

            Map<String, Object> outputs = connector.getOutputs();
            assertThat(outputs.get("mimeType")).isEqualTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            assertThat(outputs.get("success")).isEqualTo(true);
        }

        @Test
        @DisplayName("should_set_error_outputs_when_upload_fails")
        void should_set_error_outputs_when_upload_fails() throws Exception {
            Map<String, Object> params = new HashMap<>();
            params.put("serviceAccountJson", "{\"type\": \"service_account\"}");
            params.put("fileName", "fail.pdf");
            params.put("fileContent", Base64.getEncoder().encodeToString("data".getBytes()));

            connector.setInputParameters(params);
            connector.validateInputParameters();

            when(mockClient.getDriveService()).thenReturn(mockDriveService);
            when(mockClient.executeWithRetry(any(Callable.class), anyString()))
                    .thenThrow(new RuntimeException("Quota exceeded"));

            injectClient(connector, mockClient);

            assertThatThrownBy(() -> executeLogic(connector))
                    .isInstanceOf(ConnectorException.class);

            Map<String, Object> outputs = connector.getOutputs();
            assertThat(outputs.get("success")).isEqualTo(false);
            assertThat((String) outputs.get("errorMessage")).contains("Quota exceeded");
        }
    }
}
