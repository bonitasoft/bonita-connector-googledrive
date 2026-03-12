package com.bonitasoft.connectors.gdrive;

import lombok.extern.slf4j.Slf4j;

/**
 * Connector that downloads a file from Google Drive by its file ID.
 * For Google Workspace files (Docs, Sheets, Slides), an exportMimeType
 * must be specified to convert the file during download.
 */
@Slf4j
public class DownloadFileConnector extends AbstractGDriveConnector {

    // === Input parameter name constants ===
    static final String INPUT_SERVICE_ACCOUNT_KEY_JSON = "serviceAccountKeyJson";
    static final String INPUT_IMPERSONATED_USER_EMAIL = "impersonatedUserEmail";
    static final String INPUT_APPLICATION_NAME = "applicationName";
    static final String INPUT_CONNECT_TIMEOUT = "connectTimeout";
    static final String INPUT_READ_TIMEOUT = "readTimeout";
    static final String INPUT_FILE_ID = "fileId";
    static final String INPUT_EXPORT_MIME_TYPE = "exportMimeType";

    // === Output parameter name constants ===
    static final String OUTPUT_FILE_CONTENT_BASE64 = "fileContentBase64";
    static final String OUTPUT_FILE_NAME = "fileName";
    static final String OUTPUT_MIME_TYPE = "mimeType";
    static final String OUTPUT_FILE_SIZE_BYTES = "fileSizeBytes";

    @Override
    protected GDriveConfiguration buildConfiguration() {
        return GDriveConfiguration.builder()
                .serviceAccountKeyJson(readStringInput(INPUT_SERVICE_ACCOUNT_KEY_JSON))
                .impersonatedUserEmail(readStringInput(INPUT_IMPERSONATED_USER_EMAIL))
                .applicationName(readStringInput(INPUT_APPLICATION_NAME, "Bonita-GoogleDrive-Connector"))
                .connectTimeout(readIntegerInput(INPUT_CONNECT_TIMEOUT, 30000))
                .readTimeout(readIntegerInput(INPUT_READ_TIMEOUT, 60000))
                .fileId(readStringInput(INPUT_FILE_ID))
                .exportMimeType(readStringInput(INPUT_EXPORT_MIME_TYPE))
                .build();
    }

    @Override
    protected void validateConfiguration(GDriveConfiguration config) {
        super.validateConfiguration(config);
        if (config.getFileId() == null || config.getFileId().isBlank()) {
            throw new IllegalArgumentException("fileId is mandatory");
        }
    }

    @Override
    protected void doExecute() throws GDriveException {
        log.info("Executing DownloadFile connector for fileId={}", configuration.getFileId());

        GDriveClient.DownloadFileResult result = client.downloadFile(
                configuration.getFileId(),
                configuration.getExportMimeType()
        );

        setOutputParameter(OUTPUT_FILE_CONTENT_BASE64, result.fileContentBase64());
        setOutputParameter(OUTPUT_FILE_NAME, result.fileName());
        setOutputParameter(OUTPUT_MIME_TYPE, result.mimeType());
        setOutputParameter(OUTPUT_FILE_SIZE_BYTES, result.fileSizeBytes());

        log.info("DownloadFile connector executed successfully: fileName={}, size={} bytes",
                result.fileName(), result.fileSizeBytes());
    }
}
