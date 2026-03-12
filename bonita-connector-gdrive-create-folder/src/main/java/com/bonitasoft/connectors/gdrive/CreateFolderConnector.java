package com.bonitasoft.connectors.gdrive;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CreateFolderConnector extends AbstractGDriveConnector {

    // === Input parameter name constants ===
    static final String INPUT_SERVICE_ACCOUNT_KEY_JSON = "serviceAccountKeyJson";
    static final String INPUT_IMPERSONATED_USER_EMAIL = "impersonatedUserEmail";
    static final String INPUT_APPLICATION_NAME = "applicationName";
    static final String INPUT_CONNECT_TIMEOUT = "connectTimeout";
    static final String INPUT_READ_TIMEOUT = "readTimeout";
    static final String INPUT_FOLDER_NAME = "folderName";
    static final String INPUT_PARENT_FOLDER_ID = "parentFolderId";
    static final String INPUT_DESCRIPTION = "description";

    // === Output parameter name constants ===
    static final String OUTPUT_FOLDER_ID = "folderId";
    static final String OUTPUT_FOLDER_WEB_VIEW_LINK = "folderWebViewLink";

    @Override
    protected GDriveConfiguration buildConfiguration() {
        return GDriveConfiguration.builder()
                .serviceAccountKeyJson(readStringInput(INPUT_SERVICE_ACCOUNT_KEY_JSON))
                .impersonatedUserEmail(readStringInput(INPUT_IMPERSONATED_USER_EMAIL))
                .applicationName(readStringInput(INPUT_APPLICATION_NAME, "Bonita-GoogleDrive-Connector"))
                .connectTimeout(readIntegerInput(INPUT_CONNECT_TIMEOUT, 30000))
                .readTimeout(readIntegerInput(INPUT_READ_TIMEOUT, 60000))
                .folderName(readStringInput(INPUT_FOLDER_NAME))
                .parentFolderId(readStringInput(INPUT_PARENT_FOLDER_ID))
                .description(readStringInput(INPUT_DESCRIPTION))
                .build();
    }

    @Override
    protected void validateConfiguration(GDriveConfiguration config) {
        super.validateConfiguration(config);
        if (config.getFolderName() == null || config.getFolderName().isBlank()) {
            throw new IllegalArgumentException("folderName is mandatory");
        }
    }

    @Override
    protected void doExecute() throws GDriveException {
        log.info("Executing CreateFolder connector");

        GDriveClient.CreateFolderResult result = client.createFolder(
                configuration.getFolderName(),
                configuration.getParentFolderId(),
                configuration.getDescription()
        );

        setOutputParameter(OUTPUT_FOLDER_ID, result.folderId());
        setOutputParameter(OUTPUT_FOLDER_WEB_VIEW_LINK, result.folderWebViewLink());

        log.info("CreateFolder connector executed successfully, folderId={}", result.folderId());
    }
}
