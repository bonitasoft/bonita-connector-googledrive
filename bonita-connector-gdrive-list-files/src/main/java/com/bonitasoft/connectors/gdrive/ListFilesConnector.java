package com.bonitasoft.connectors.gdrive;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

@Slf4j
public class ListFilesConnector extends AbstractGDriveConnector {

    // Input parameter names — Connection
    static final String INPUT_SERVICE_ACCOUNT_KEY_JSON = "serviceAccountKeyJson";
    static final String INPUT_IMPERSONATED_USER_EMAIL = "impersonatedUserEmail";
    static final String INPUT_APPLICATION_NAME = "applicationName";
    static final String INPUT_CONNECT_TIMEOUT = "connectTimeout";
    static final String INPUT_READ_TIMEOUT = "readTimeout";

    // Input parameter names — Search
    static final String INPUT_PARENT_FOLDER_ID = "parentFolderId";
    static final String INPUT_SEARCH_QUERY = "searchQuery";
    static final String INPUT_MIME_TYPE_FILTER = "mimeTypeFilter";

    // Input parameter names — Configuration
    static final String INPUT_MAX_RESULTS = "maxResults";
    static final String INPUT_ORDER_BY = "orderBy";
    static final String INPUT_INCLUDE_FILES = "includeFiles";
    static final String INPUT_INCLUDE_FOLDERS = "includeFolders";

    // Output parameter names
    static final String OUTPUT_FILES = "files";
    static final String OUTPUT_TOTAL_COUNT = "totalCount";
    static final String OUTPUT_NEXT_PAGE_TOKEN = "nextPageToken";

    @Override
    protected GDriveConfiguration buildConfiguration() {
        return GDriveConfiguration.builder()
                .serviceAccountKeyJson(readStringInput(INPUT_SERVICE_ACCOUNT_KEY_JSON))
                .impersonatedUserEmail(readStringInput(INPUT_IMPERSONATED_USER_EMAIL))
                .applicationName(readStringInput(INPUT_APPLICATION_NAME, "Bonita-GoogleDrive-Connector"))
                .connectTimeout(readIntegerInput(INPUT_CONNECT_TIMEOUT, 30000))
                .readTimeout(readIntegerInput(INPUT_READ_TIMEOUT, 60000))
                .parentFolderId(readStringInput(INPUT_PARENT_FOLDER_ID))
                .searchQuery(readStringInput(INPUT_SEARCH_QUERY))
                .mimeTypeFilter(readStringInput(INPUT_MIME_TYPE_FILTER))
                .maxResults(readIntegerInput(INPUT_MAX_RESULTS, 100))
                .orderBy(readStringInput(INPUT_ORDER_BY, "modifiedTime desc"))
                .includeFiles(readBooleanInput(INPUT_INCLUDE_FILES, true))
                .includeFolders(readBooleanInput(INPUT_INCLUDE_FOLDERS, true))
                .build();
    }

    @Override
    protected void doExecute() throws GDriveException {
        log.info("Listing files: parentFolderId='{}', searchQuery='{}', mimeTypeFilter='{}', maxResults={}, orderBy='{}', includeFiles={}, includeFolders={}",
                configuration.getParentFolderId(),
                configuration.getSearchQuery(),
                configuration.getMimeTypeFilter(),
                configuration.getMaxResults(),
                configuration.getOrderBy(),
                configuration.isIncludeFiles(),
                configuration.isIncludeFolders());

        GDriveClient.ListFilesResult result = client.listFiles(
                configuration.getParentFolderId(),
                configuration.getSearchQuery(),
                configuration.getMimeTypeFilter(),
                configuration.getMaxResults(),
                configuration.getOrderBy(),
                configuration.isIncludeFiles(),
                configuration.isIncludeFolders()
        );

        setOutputParameter(OUTPUT_FILES, result.files());
        setOutputParameter(OUTPUT_TOTAL_COUNT, result.totalCount());
        setOutputParameter(OUTPUT_NEXT_PAGE_TOKEN, result.nextPageToken());

        log.info("Listed {} files successfully", result.totalCount());
    }
}
