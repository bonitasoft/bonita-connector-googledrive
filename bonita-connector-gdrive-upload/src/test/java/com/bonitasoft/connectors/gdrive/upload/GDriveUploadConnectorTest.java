package com.bonitasoft.connectors.gdrive.upload;

import org.bonitasoft.engine.connector.ConnectorValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
}
