package com.bonitasoft.connectors.gdrive;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("GDriveConfiguration")
class GDriveConfigurationTest {

    @Nested
    @DisplayName("Factory method")
    class FactoryMethod {

        @Test
        @DisplayName("should create configuration from input map")
        void shouldCreateFromInputMap() {
            Map<String, Object> inputs = new HashMap<>();
            inputs.put("serviceAccountJson", "{\"type\": \"service_account\"}");
            inputs.put("applicationName", "Test-App");
            inputs.put("connectTimeout", 5000);
            inputs.put("readTimeout", 10000);

            GDriveConfiguration config = GDriveConfiguration.from(inputs);

            assertThat(config.serviceAccountJson()).isEqualTo("{\"type\": \"service_account\"}");
            assertThat(config.applicationName()).isEqualTo("Test-App");
            assertThat(config.connectTimeout()).isEqualTo(5000);
            assertThat(config.readTimeout()).isEqualTo(10000);
        }

        @Test
        @DisplayName("should apply default values for missing optional inputs")
        void shouldApplyDefaults() {
            Map<String, Object> inputs = new HashMap<>();
            inputs.put("serviceAccountJson", "{\"type\": \"service_account\"}");

            GDriveConfiguration config = GDriveConfiguration.from(inputs);

            assertThat(config.applicationName()).isEqualTo("Bonita-GDrive-Connector");
            assertThat(config.connectTimeout()).isEqualTo(30000);
            assertThat(config.readTimeout()).isEqualTo(60000);
            assertThat(config.scopes()).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("Authentication detection")
    class AuthDetection {

        @Test
        @DisplayName("should detect Service Account auth")
        void shouldDetectServiceAccountAuth() {
            var config = new GDriveConfiguration(
                "{\"type\": \"service_account\"}",
                null, null, null, null, null, null, 30000, 60000
            );

            assertThat(config.isServiceAccountAuth()).isTrue();
            assertThat(config.isOAuthAuth()).isFalse();
            assertThat(config.hasValidAuth()).isTrue();
        }

        @Test
        @DisplayName("should detect OAuth auth")
        void shouldDetectOAuthAuth() {
            var config = new GDriveConfiguration(
                null, null,
                "client-id", "client-secret", "refresh-token",
                null, null, 30000, 60000
            );

            assertThat(config.isServiceAccountAuth()).isFalse();
            assertThat(config.isOAuthAuth()).isTrue();
            assertThat(config.hasValidAuth()).isTrue();
        }

        @Test
        @DisplayName("should report no valid auth when nothing configured")
        void shouldReportNoValidAuth() {
            var config = new GDriveConfiguration(
                null, null, null, null, null, null, null, 30000, 60000
            );

            assertThat(config.hasValidAuth()).isFalse();
        }
    }

    @Nested
    @DisplayName("Validation")
    class Validation {

        @Test
        @DisplayName("should throw on missing authentication")
        void shouldThrowOnMissingAuth() {
            var config = new GDriveConfiguration(
                null, null, null, null, null, null, null, 30000, 60000
            );

            assertThatThrownBy(config::validate)
                .isInstanceOf(GDriveException.ValidationException.class)
                .hasMessageContaining("Authentication required");
        }

        @Test
        @DisplayName("should throw on file path instead of JSON")
        void shouldThrowOnFilePath() {
            var config = new GDriveConfiguration(
                "/path/to/credentials.json",
                null, null, null, null, null, null, 30000, 60000
            );

            assertThatThrownBy(config::validate)
                .isInstanceOf(GDriveException.ValidationException.class)
                .hasMessageContaining("JSON content");
        }

        @Test
        @DisplayName("should pass validation with valid Service Account")
        void shouldPassWithValidServiceAccount() {
            var config = new GDriveConfiguration(
                "{\"type\": \"service_account\"}",
                null, null, null, null, null, null, 30000, 60000
            );

            config.validate(); // Should not throw
        }
    }
}
