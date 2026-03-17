package com.bonitasoft.connectors.gdrive.createfolder;

import java.util.List;
import java.util.Map;

import com.bonitasoft.connectors.gdrive.AbstractGDriveConnector;
import com.bonitasoft.connectors.gdrive.GDriveException;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;

import lombok.extern.slf4j.Slf4j;

/**
 * Google Drive Create Folder connector.
 */
@Slf4j
public class GDriveCreateFolderConnector extends AbstractGDriveConnector {

    public static final String INPUT_FOLDER_NAME = "folderName";
    public static final String INPUT_PARENT_FOLDER_ID = "parentFolderId";
    public static final String INPUT_DESCRIPTION = "description";
    public static final String INPUT_CUSTOM_PROPERTIES = "customProperties";

    public static final String OUTPUT_FOLDER_ID = "folderId";
    public static final String OUTPUT_FOLDER_NAME = "folderName";
    public static final String OUTPUT_WEB_VIEW_LINK = "webViewLink";

    private static final String FOLDER_MIME_TYPE = "application/vnd.google-apps.folder";

    @Override
    protected String getConnectorName() {
        return "GDrive-CreateFolder";
    }

    @Override
    protected void validateOperationInputs(List<String> errors) {
        String folderName = getStringInput(INPUT_FOLDER_NAME);
        if (folderName == null || folderName.isBlank()) {
            errors.add("folderName is required");
        }
    }

    @Override
    protected void executeOperation(Drive driveService) throws GDriveException {
        String folderName = getStringInput(INPUT_FOLDER_NAME);
        String parentFolderId = getStringInput(INPUT_PARENT_FOLDER_ID);
        String description = getStringInput(INPUT_DESCRIPTION);
        Map<String, String> customProperties = getMapInput(INPUT_CUSTOM_PROPERTIES);

        log.info("Creating folder: {}", folderName);

        File folderMetadata = new File();
        folderMetadata.setName(folderName);
        folderMetadata.setMimeType(FOLDER_MIME_TYPE);
        folderMetadata.setDescription(description);

        if (parentFolderId != null && !parentFolderId.isBlank()) {
            folderMetadata.setParents(List.of(parentFolderId));
        }

        if (customProperties != null && !customProperties.isEmpty()) {
            folderMetadata.setAppProperties(customProperties);
        }

        File createdFolder = client.executeWithRetry(() -> {
            return driveService.files().create(folderMetadata)
                    .setSupportsAllDrives(true)
                    .setFields("id,name,webViewLink")
                    .execute();
        }, "create folder '" + folderName + "'");

        setOutputParameter(OUTPUT_FOLDER_ID, createdFolder.getId());
        setOutputParameter(OUTPUT_FOLDER_NAME, createdFolder.getName());
        setOutputParameter(OUTPUT_WEB_VIEW_LINK, createdFolder.getWebViewLink());

        log.info("Folder created: {} (ID: {})", createdFolder.getName(), createdFolder.getId());
    }
}
