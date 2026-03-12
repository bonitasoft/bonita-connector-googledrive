package com.bonitasoft.connectors.gdrive;

import lombok.extern.slf4j.Slf4j;

import java.util.Base64;

@Slf4j
public class UploadFileConnector extends AbstractGDriveConnector {

    // Input parameter names
    static final String INPUT_SERVICE_ACCOUNT_KEY_JSON = "serviceAccountKeyJson";
    static final String INPUT_IMPERSONATED_USER_EMAIL = "impersonatedUserEmail";
    static final String INPUT_APPLICATION_NAME = "applicationName";
    static final String INPUT_CONNECT_TIMEOUT = "connectTimeout";
    static final String INPUT_READ_TIMEOUT = "readTimeout";
    static final String INPUT_FILE_NAME = "fileName";
    static final String INPUT_FILE_CONTENT_BASE64 = "fileContentBase64";
    static final String INPUT_MIME_TYPE = "mimeType";
    static final String INPUT_PARENT_FOLDER_ID = "parentFolderId";
    static final String INPUT_UPLOAD_STRATEGY = "uploadStrategy";
    static final String INPUT_DESCRIPTION = "description";

    // Output parameter names
    static final String OUTPUT_FILE_ID = "fileId";
    static final String OUTPUT_FILE_WEB_VIEW_LINK = "fileWebViewLink";
    static final String OUTPUT_FILE_WEB_CONTENT_LINK = "fileWebContentLink";

    @Override
    protected GDriveConfiguration buildConfiguration() {
        return GDriveConfiguration.builder()
                .serviceAccountKeyJson(readStringInput(INPUT_SERVICE_ACCOUNT_KEY_JSON))
                .impersonatedUserEmail(readStringInput(INPUT_IMPERSONATED_USER_EMAIL))
                .applicationName(readStringInput(INPUT_APPLICATION_NAME, "Bonita-GoogleDrive-Connector"))
                .connectTimeout(readIntegerInput(INPUT_CONNECT_TIMEOUT, 30000))
                .readTimeout(readIntegerInput(INPUT_READ_TIMEOUT, 60000))
                .fileName(readStringInput(INPUT_FILE_NAME))
                .fileContentBase64(readStringInput(INPUT_FILE_CONTENT_BASE64))
                .mimeType(readStringInput(INPUT_MIME_TYPE, "application/octet-stream"))
                .parentFolderId(readStringInput(INPUT_PARENT_FOLDER_ID))
                .uploadStrategy(readStringInput(INPUT_UPLOAD_STRATEGY, "AUTO"))
                .description(readStringInput(INPUT_DESCRIPTION))
                .build();
    }

    @Override
    protected void validateConfiguration(GDriveConfiguration config) {
        super.validateConfiguration(config);
        if (config.getFileName() == null || config.getFileName().isBlank()) {
            throw new IllegalArgumentException("fileName is mandatory");
        }
        if (config.getFileContentBase64() == null || config.getFileContentBase64().isBlank()) {
            throw new IllegalArgumentException("fileContentBase64 is mandatory");
        }
    }

    @Override
    protected void doExecute() throws GDriveException {
        log.info("Uploading file '{}' with mimeType '{}' to parentFolderId '{}'",
                configuration.getFileName(), configuration.getMimeType(), configuration.getParentFolderId());

        byte[] fileContent;
        try {
            fileContent = Base64.getDecoder().decode(configuration.getFileContentBase64());
        } catch (IllegalArgumentException e) {
            throw new GDriveException("Invalid base64 content: " + e.getMessage(), e);
        }

        GDriveClient.UploadFileResult result = client.uploadFile(
                configuration.getFileName(),
                fileContent,
                configuration.getMimeType(),
                configuration.getParentFolderId(),
                configuration.getUploadStrategy(),
                configuration.getDescription()
        );

        setOutputParameter(OUTPUT_FILE_ID, result.fileId());
        setOutputParameter(OUTPUT_FILE_WEB_VIEW_LINK, result.webViewLink());
        setOutputParameter(OUTPUT_FILE_WEB_CONTENT_LINK, result.webContentLink());

        log.info("File uploaded successfully with id '{}'", result.fileId());
    }
}
