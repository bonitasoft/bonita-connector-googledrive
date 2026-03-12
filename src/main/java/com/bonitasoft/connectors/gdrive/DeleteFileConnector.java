package com.bonitasoft.connectors.gdrive;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DeleteFileConnector extends AbstractGDriveConnector {

    // Input parameter names
    static final String INPUT_SERVICE_ACCOUNT_KEY_JSON = "serviceAccountKeyJson";
    static final String INPUT_IMPERSONATED_USER_EMAIL = "impersonatedUserEmail";
    static final String INPUT_APPLICATION_NAME = "applicationName";
    static final String INPUT_CONNECT_TIMEOUT = "connectTimeout";
    static final String INPUT_READ_TIMEOUT = "readTimeout";
    static final String INPUT_FILE_ID = "fileId";
    static final String INPUT_PERMANENT = "permanent";

    // Output parameter names
    static final String OUTPUT_DELETED_FILE_ID = "deletedFileId";
    static final String OUTPUT_PERMANENT = "permanent";

    @Override
    protected GDriveConfiguration buildConfiguration() {
        return GDriveConfiguration.builder()
                .serviceAccountKeyJson(readStringInput(INPUT_SERVICE_ACCOUNT_KEY_JSON))
                .impersonatedUserEmail(readStringInput(INPUT_IMPERSONATED_USER_EMAIL))
                .applicationName(readStringInput(INPUT_APPLICATION_NAME, "Bonita-GoogleDrive-Connector"))
                .connectTimeout(readIntegerInput(INPUT_CONNECT_TIMEOUT, 30000))
                .readTimeout(readIntegerInput(INPUT_READ_TIMEOUT, 60000))
                .fileId(readStringInput(INPUT_FILE_ID))
                .permanent(readBooleanInput(INPUT_PERMANENT, false))
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
        log.info("Deleting file '{}' (permanent={})", configuration.getFileId(), configuration.isPermanent());

        GDriveClient.DeleteFileResult result = client.deleteFile(
                configuration.getFileId(),
                configuration.isPermanent()
        );

        setOutputParameter(OUTPUT_DELETED_FILE_ID, result.deletedFileId());
        setOutputParameter(OUTPUT_PERMANENT, result.permanent());

        log.info("File '{}' deleted successfully (permanent={})", result.deletedFileId(), result.permanent());
    }
}
