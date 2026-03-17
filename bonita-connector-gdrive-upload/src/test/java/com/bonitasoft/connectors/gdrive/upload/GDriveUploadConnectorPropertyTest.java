package com.bonitasoft.connectors.gdrive.upload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import net.jqwik.api.*;
import net.jqwik.api.constraints.*;

import org.bonitasoft.engine.connector.ConnectorValidationException;

/**
 * Property-based tests for GDriveUploadConnector using jqwik.
 * Tests input validation with randomly generated data to find edge cases.
 */
class GDriveUploadConnectorPropertyTest {

    @Property(tries = 100)
    @Label("Valid inputs should always pass validation")
    void validInputsShouldAlwaysPassValidation(
            @ForAll @AlphaChars @StringLength(min = 1, max = 100) String fileNameBase,
            @ForAll @AlphaChars @StringLength(min = 1, max = 100) String rawContent,
            @ForAll @IntRange(min = 1, max = 300_000) int connectTimeout,
            @ForAll @IntRange(min = 1, max = 300_000) int readTimeout
    ) throws ConnectorValidationException {
        // Ensure fileName is never blank by appending extension
        String fileName = fileNameBase + ".txt";
        
        var connector = new GDriveUploadConnector();
        Map<String, Object> inputs = new HashMap<>();

        inputs.put("serviceAccountJson", "{\"type\": \"service_account\"}");
        inputs.put("fileName", fileName);
        inputs.put("fileContent", Base64.getEncoder().encodeToString(rawContent.getBytes()));
        inputs.put("connectTimeout", connectTimeout);
        inputs.put("readTimeout", readTimeout);

        connector.setInputParameters(inputs);
        connector.validateInputParameters();
        // No exception = property holds
    }

    @Property(tries = 50)
    @Label("Null mandatory parameters should always fail validation")
    void nullMandatoryParamsShouldFailValidation(
            @ForAll @IntRange(min = 1, max = 300_000) int connectTimeout
    ) {
        var connector = new GDriveUploadConnector();
        Map<String, Object> inputs = new HashMap<>();

        // Set only optional params, leave mandatory ones null
        inputs.put("connectTimeout", connectTimeout);
        inputs.put("readTimeout", 60000);

        connector.setInputParameters(inputs);
        assertThatThrownBy(connector::validateInputParameters)
                .isInstanceOf(ConnectorValidationException.class);
    }

    @Property(tries = 50)
    @Label("Empty fileName should always fail validation")
    void emptyFileNameShouldFailValidation(
            @ForAll @StringLength(min = 1, max = 100) String rawContent
    ) {
        var connector = new GDriveUploadConnector();
        Map<String, Object> inputs = new HashMap<>();

        inputs.put("serviceAccountJson", "{\"type\": \"service_account\"}");
        inputs.put("fileName", "");
        inputs.put("fileContent", Base64.getEncoder().encodeToString(rawContent.getBytes()));

        connector.setInputParameters(inputs);
        assertThatThrownBy(connector::validateInputParameters)
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("fileName");
    }

    @Property(tries = 50)
    @Label("Invalid Base64 content should always fail validation")
    void invalidBase64ShouldFailValidation(
            @ForAll @StringLength(min = 1, max = 50) String fileName
    ) {
        var connector = new GDriveUploadConnector();
        Map<String, Object> inputs = new HashMap<>();

        inputs.put("serviceAccountJson", "{\"type\": \"service_account\"}");
        inputs.put("fileName", fileName);
        inputs.put("fileContent", "!!!not-valid-base64!!!");

        connector.setInputParameters(inputs);
        assertThatThrownBy(connector::validateInputParameters)
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("Base64");
    }

    @Property(tries = 30)
    @Label("Negative timeouts should always fail validation")
    void negativeTimeoutsShouldFailValidation(
            @ForAll @IntRange(min = Integer.MIN_VALUE, max = -1) int badTimeout
    ) {
        var connector = new GDriveUploadConnector();
        Map<String, Object> inputs = new HashMap<>();

        inputs.put("serviceAccountJson", "{\"type\": \"service_account\"}");
        inputs.put("fileName", "test.pdf");
        inputs.put("fileContent", Base64.getEncoder().encodeToString("test".getBytes()));
        inputs.put("connectTimeout", badTimeout);
        inputs.put("readTimeout", 60000);

        connector.setInputParameters(inputs);
        assertThatThrownBy(connector::validateInputParameters)
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("connectTimeout");
    }

    @Property(tries = 50)
    @Label("File path instead of JSON should fail validation")
    void filePathShouldFailValidation(
            @ForAll @StringLength(min = 1, max = 100) String path
    ) {
        // Skip if the path accidentally starts with { (would look like JSON)
        Assume.that(!path.trim().startsWith("{"));
        
        var connector = new GDriveUploadConnector();
        Map<String, Object> inputs = new HashMap<>();

        inputs.put("serviceAccountJson", "/path/to/" + path + ".json");
        inputs.put("fileName", "test.pdf");
        inputs.put("fileContent", Base64.getEncoder().encodeToString("test".getBytes()));

        connector.setInputParameters(inputs);
        assertThatThrownBy(connector::validateInputParameters)
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("JSON content");
    }
}
