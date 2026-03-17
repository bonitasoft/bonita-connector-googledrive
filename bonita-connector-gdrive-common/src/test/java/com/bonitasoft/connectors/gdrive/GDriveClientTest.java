package com.bonitasoft.connectors.gdrive;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("GDriveClient")
class GDriveClientTest {

    @Nested
    @DisplayName("Retry logic")
    class RetryLogic {

        @Test
        @DisplayName("should not retry non-retryable exceptions")
        void shouldNotRetryNonRetryable() {
            AtomicInteger attempts = new AtomicInteger(0);
            
            // Simulate a non-retryable error (400)
            var exception = new GDriveException.ExecutionException("Bad request", 400);
            
            assertThat(exception.isRetryable()).isFalse();
        }

        @Test
        @DisplayName("should mark 429 as retryable")
        void shouldMark429AsRetryable() {
            var exception = new GDriveException.ExecutionException("Rate limited", 429);
            assertThat(exception.isRetryable()).isTrue();
        }

        @Test
        @DisplayName("should mark 500 as retryable")
        void shouldMark500AsRetryable() {
            var exception = new GDriveException.ExecutionException("Server error", 500);
            assertThat(exception.isRetryable()).isTrue();
        }

        @Test
        @DisplayName("should mark 503 as retryable")
        void shouldMark503AsRetryable() {
            var exception = new GDriveException.ExecutionException("Service unavailable", 503);
            assertThat(exception.isRetryable()).isTrue();
        }

        @Test
        @DisplayName("should not mark 404 as retryable")
        void shouldNotMark404AsRetryable() {
            var exception = new GDriveException.ExecutionException("Not found", 404);
            assertThat(exception.isRetryable()).isFalse();
        }
    }

    @Nested
    @DisplayName("Configuration validation")
    class ConfigValidation {

        @Test
        @DisplayName("should create configuration with defaults")
        void shouldCreateWithDefaults() {
            var config = new GDriveConfiguration(
                "{\"type\": \"service_account\"}",
                null, null, null, null,
                null, null, // will use defaults
                0, 0        // will use defaults
            );

            assertThat(config.applicationName()).isEqualTo("Bonita-GDrive-Connector");
            assertThat(config.connectTimeout()).isEqualTo(30_000);
            assertThat(config.readTimeout()).isEqualTo(60_000);
            assertThat(config.scopes()).isNotEmpty();
        }

        @Test
        @DisplayName("should preserve custom values")
        void shouldPreserveCustomValues() {
            var config = new GDriveConfiguration(
                "{\"type\": \"service_account\"}",
                "user@domain.com",
                null, null, null,
                "Custom-App",
                List.of("https://www.googleapis.com/auth/drive"),
                5000, 10000
            );

            assertThat(config.applicationName()).isEqualTo("Custom-App");
            assertThat(config.impersonateUser()).isEqualTo("user@domain.com");
            assertThat(config.connectTimeout()).isEqualTo(5000);
            assertThat(config.readTimeout()).isEqualTo(10000);
        }
    }
}
