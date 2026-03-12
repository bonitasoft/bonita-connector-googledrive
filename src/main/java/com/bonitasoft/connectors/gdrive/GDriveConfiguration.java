package com.bonitasoft.connectors.gdrive;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GDriveConfiguration {

    // === Connection / Auth parameters (Project/Runtime scope) ===
    private String serviceAccountKeyJson;
    private String impersonatedUserEmail;

    @Builder.Default
    private String applicationName = "Bonita-GoogleDrive-Connector";

    @Builder.Default
    private int connectTimeout = 30000;

    @Builder.Default
    private int readTimeout = 60000;

    // === Operation parameters (Connector scope) ===
    // Upload
    private String fileName;
    private String fileContentBase64;
    private String mimeType;
    private String parentFolderId;
    private String uploadStrategy;
    private String description;

    // Download
    private String fileId;
    private String exportMimeType;

    // Create Folder
    private String folderName;

    // Get File
    private String fields;

    // List Files
    private String searchQuery;
    private String mimeTypeFilter;
    @Builder.Default
    private int maxResults = 100;
    @Builder.Default
    private String orderBy = "modifiedTime desc";
    @Builder.Default
    private boolean includeFiles = true;
    @Builder.Default
    private boolean includeFolders = true;

    // Move File
    private String newParentFolderId;
    private String newName;
    @Builder.Default
    private boolean removeFromCurrentParents = true;

    // Delete File
    @Builder.Default
    private boolean permanent = false;

    // === Retry ===
    @Builder.Default
    private int maxRetries = 5;
}
