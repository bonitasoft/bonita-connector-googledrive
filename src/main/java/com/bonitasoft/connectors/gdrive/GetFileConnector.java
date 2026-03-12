package com.bonitasoft.connectors.gdrive;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GetFileConnector extends AbstractGDriveConnector {

    // === Input parameter name constants ===
    static final String INPUT_SERVICE_ACCOUNT_KEY_JSON = "serviceAccountKeyJson";
    static final String INPUT_IMPERSONATED_USER_EMAIL = "impersonatedUserEmail";
    static final String INPUT_APPLICATION_NAME = "applicationName";
    static final String INPUT_CONNECT_TIMEOUT = "connectTimeout";
    static final String INPUT_READ_TIMEOUT = "readTimeout";
    static final String INPUT_FILE_ID = "fileId";
    static final String DEFAULT_FIELDS = "id,name,mimeType,webViewLink,webContentLink,size,createdTime,modifiedTime,owners,parents";

    // === Output parameter name constants ===
    static final String OUTPUT_FILE_ID = "fileId";
    static final String OUTPUT_FILE_NAME = "fileName";
    static final String OUTPUT_MIME_TYPE = "mimeType";
    static final String OUTPUT_WEB_VIEW_LINK = "webViewLink";
    static final String OUTPUT_WEB_CONTENT_LINK = "webContentLink";
    static final String OUTPUT_SIZE_BYTES = "sizeBytes";
    static final String OUTPUT_CREATED_TIME = "createdTime";
    static final String OUTPUT_MODIFIED_TIME = "modifiedTime";
    static final String OUTPUT_OWNER_EMAIL = "ownerEmail";

    @Override
    protected GDriveConfiguration buildConfiguration() {
        return GDriveConfiguration.builder()
                .serviceAccountKeyJson(readStringInput(INPUT_SERVICE_ACCOUNT_KEY_JSON))
                .impersonatedUserEmail(readStringInput(INPUT_IMPERSONATED_USER_EMAIL))
                .applicationName(readStringInput(INPUT_APPLICATION_NAME, "Bonita-GoogleDrive-Connector"))
                .connectTimeout(readIntegerInput(INPUT_CONNECT_TIMEOUT, 30000))
                .readTimeout(readIntegerInput(INPUT_READ_TIMEOUT, 60000))
                .fileId(readStringInput(INPUT_FILE_ID))
                .fields(DEFAULT_FIELDS)
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
        log.info("Executing GetFile connector for fileId={}", configuration.getFileId());

        GDriveClient.GetFileResult result = client.getFile(
                configuration.getFileId(),
                configuration.getFields()
        );

        setOutputParameter(OUTPUT_FILE_ID, result.fileId());
        setOutputParameter(OUTPUT_FILE_NAME, result.fileName());
        setOutputParameter(OUTPUT_MIME_TYPE, result.mimeType());
        setOutputParameter(OUTPUT_WEB_VIEW_LINK, result.webViewLink());
        setOutputParameter(OUTPUT_WEB_CONTENT_LINK, result.webContentLink());
        setOutputParameter(OUTPUT_SIZE_BYTES, result.sizeBytes());
        setOutputParameter(OUTPUT_CREATED_TIME, result.createdTime());
        setOutputParameter(OUTPUT_MODIFIED_TIME, result.modifiedTime());
        setOutputParameter(OUTPUT_OWNER_EMAIL, result.ownerEmail());

        log.info("GetFile connector executed successfully for fileId={}", result.fileId());
    }
}
