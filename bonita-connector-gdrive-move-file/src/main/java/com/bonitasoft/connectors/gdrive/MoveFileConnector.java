package com.bonitasoft.connectors.gdrive;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MoveFileConnector extends AbstractGDriveConnector {

    // Input parameter names
    static final String INPUT_SERVICE_ACCOUNT_KEY_JSON = "serviceAccountKeyJson";
    static final String INPUT_IMPERSONATED_USER_EMAIL = "impersonatedUserEmail";
    static final String INPUT_APPLICATION_NAME = "applicationName";
    static final String INPUT_CONNECT_TIMEOUT = "connectTimeout";
    static final String INPUT_READ_TIMEOUT = "readTimeout";
    static final String INPUT_FILE_ID = "fileId";
    static final String INPUT_NEW_PARENT_FOLDER_ID = "newParentFolderId";
    static final String INPUT_NEW_NAME = "newName";
    static final String INPUT_REMOVE_FROM_CURRENT_PARENTS = "removeFromCurrentParents";

    // Output parameter names
    static final String OUTPUT_FILE_ID = "fileId";
    static final String OUTPUT_FILE_NAME = "fileName";
    static final String OUTPUT_NEW_PARENT_FOLDER_ID = "newParentFolderId";
    static final String OUTPUT_WEB_VIEW_LINK = "webViewLink";

    @Override
    protected GDriveConfiguration buildConfiguration() {
        return GDriveConfiguration.builder()
                .serviceAccountKeyJson(readStringInput(INPUT_SERVICE_ACCOUNT_KEY_JSON))
                .impersonatedUserEmail(readStringInput(INPUT_IMPERSONATED_USER_EMAIL))
                .applicationName(readStringInput(INPUT_APPLICATION_NAME, "Bonita-GoogleDrive-Connector"))
                .connectTimeout(readIntegerInput(INPUT_CONNECT_TIMEOUT, 30000))
                .readTimeout(readIntegerInput(INPUT_READ_TIMEOUT, 60000))
                .fileId(readStringInput(INPUT_FILE_ID))
                .newParentFolderId(readStringInput(INPUT_NEW_PARENT_FOLDER_ID))
                .newName(readStringInput(INPUT_NEW_NAME))
                .removeFromCurrentParents(readBooleanInput(INPUT_REMOVE_FROM_CURRENT_PARENTS, true))
                .build();
    }

    @Override
    protected void validateConfiguration(GDriveConfiguration config) {
        super.validateConfiguration(config);
        if (config.getFileId() == null || config.getFileId().isBlank()) {
            throw new IllegalArgumentException("fileId is mandatory");
        }
        boolean hasNewParent = config.getNewParentFolderId() != null && !config.getNewParentFolderId().isBlank();
        boolean hasNewName = config.getNewName() != null && !config.getNewName().isBlank();
        if (!hasNewParent && !hasNewName) {
            throw new IllegalArgumentException("At least one of newParentFolderId or newName must be provided");
        }
    }

    @Override
    protected void doExecute() throws GDriveException {
        log.info("Moving/renaming file '{}' to parent '{}' with new name '{}'",
                configuration.getFileId(), configuration.getNewParentFolderId(), configuration.getNewName());

        GDriveClient.MoveFileResult result = client.moveFile(
                configuration.getFileId(),
                configuration.getNewParentFolderId(),
                configuration.getNewName(),
                configuration.isRemoveFromCurrentParents()
        );

        setOutputParameter(OUTPUT_FILE_ID, result.fileId());
        setOutputParameter(OUTPUT_FILE_NAME, result.fileName());
        setOutputParameter(OUTPUT_NEW_PARENT_FOLDER_ID, result.newParentFolderId());
        setOutputParameter(OUTPUT_WEB_VIEW_LINK, result.webViewLink());

        log.info("File moved/renamed successfully. New id='{}', name='{}', parent='{}'",
                result.fileId(), result.fileName(), result.newParentFolderId());
    }
}
