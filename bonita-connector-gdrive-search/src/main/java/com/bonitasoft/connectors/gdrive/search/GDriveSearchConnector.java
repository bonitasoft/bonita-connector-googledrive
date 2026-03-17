package com.bonitasoft.connectors.gdrive.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.bonitasoft.connectors.gdrive.AbstractGDriveConnector;
import com.bonitasoft.connectors.gdrive.GDriveException;
import com.bonitasoft.connectors.gdrive.model.FileMetadata;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

import lombok.extern.slf4j.Slf4j;

/**
 * Google Drive Search connector.
 */
@Slf4j
public class GDriveSearchConnector extends AbstractGDriveConnector {

    public static final String INPUT_QUERY = "query";
    public static final String INPUT_NAME_CONTAINS = "nameContains";
    public static final String INPUT_MIME_TYPE = "mimeType";
    public static final String INPUT_PARENT_FOLDER_ID = "parentFolderId";
    public static final String INPUT_FULL_TEXT_SEARCH = "fullTextSearch";
    public static final String INPUT_MODIFIED_AFTER = "modifiedAfter";
    public static final String INPUT_MODIFIED_BEFORE = "modifiedBefore";
    public static final String INPUT_INCLUDE_TRASHED = "includeTrashed";
    public static final String INPUT_MAX_RESULTS = "maxResults";
    public static final String INPUT_ORDER_BY = "orderBy";

    public static final String OUTPUT_FILES = "files";
    public static final String OUTPUT_TOTAL_RESULTS = "totalResults";
    public static final String OUTPUT_NEXT_PAGE_TOKEN = "nextPageToken";

    @Override
    protected String getConnectorName() {
        return "GDrive-Search";
    }

    @Override
    protected void validateOperationInputs(List<String> errors) {
        // At least one search criterion should be provided
        String query = getStringInput(INPUT_QUERY);
        String nameContains = getStringInput(INPUT_NAME_CONTAINS);
        String parentFolderId = getStringInput(INPUT_PARENT_FOLDER_ID);
        String mimeType = getStringInput(INPUT_MIME_TYPE);
        String fullTextSearch = getStringInput(INPUT_FULL_TEXT_SEARCH);

        boolean hasQuery = query != null && !query.isBlank();
        boolean hasNameFilter = nameContains != null && !nameContains.isBlank();
        boolean hasParentFilter = parentFolderId != null && !parentFolderId.isBlank();
        boolean hasMimeFilter = mimeType != null && !mimeType.isBlank();
        boolean hasFullText = fullTextSearch != null && !fullTextSearch.isBlank();

        if (!hasQuery && !hasNameFilter && !hasParentFilter && !hasMimeFilter && !hasFullText) {
            errors.add("At least one search criterion is required: query, nameContains, parentFolderId, mimeType, or fullTextSearch");
        }

        // Validate input lengths
        if (hasQuery && query.length() > MAX_QUERY_LENGTH) {
            errors.add("query exceeds maximum length (" + MAX_QUERY_LENGTH + " chars)");
        }
        if (hasNameFilter && nameContains.length() > MAX_STRING_INPUT_LENGTH) {
            errors.add("nameContains exceeds maximum length (" + MAX_STRING_INPUT_LENGTH + " chars)");
        }
        if (hasFullText && fullTextSearch.length() > MAX_QUERY_LENGTH) {
            errors.add("fullTextSearch exceeds maximum length (" + MAX_QUERY_LENGTH + " chars)");
        }
    }

    @Override
    protected void executeOperation(Drive driveService) throws GDriveException {
        String query = buildQuery();
        int maxResults = getIntInputOrDefault(INPUT_MAX_RESULTS, 100);
        String orderBy = getStringInputOrDefault(INPUT_ORDER_BY, "modifiedTime desc");

        log.info("========== GDrive Search START ==========");
        log.info("Query: {}", query);
        log.info("Max results: {}, Order by: {}", maxResults, orderBy);

        long startTime = System.nanoTime();

        FileList result = client.executeWithRetry(() -> {
            return driveService.files().list()
                    .setQ(query)
                    .setPageSize(Math.min(maxResults, 1000))
                    .setOrderBy(orderBy)
                    .setIncludeItemsFromAllDrives(true)
                    .setSupportsAllDrives(true)
                    .setCorpora("allDrives")
                    .setFields("nextPageToken,files(id,name,mimeType,size,md5Checksum,webViewLink,webContentLink,parents,createdTime,modifiedTime,trashed)")
                    .execute();
        }, "search files");

        long elapsedNanos = System.nanoTime() - startTime;
        double elapsedMs = elapsedNanos / 1_000_000.0;
        double elapsedSec = elapsedMs / 1_000.0;

        List<File> files = result.getFiles();
        List<Map<String, Object>> fileList = files != null ?
                files.stream()
                    .map(f -> FileMetadata.fromDriveFile(f).toMap())
                    .collect(Collectors.toList()) :
                new ArrayList<>();

        setOutputParameter(OUTPUT_FILES, fileList);
        setOutputParameter(OUTPUT_TOTAL_RESULTS, fileList.size());
        setOutputParameter(OUTPUT_NEXT_PAGE_TOKEN, result.getNextPageToken());

        log.info("---------- Search Results ----------");
        log.info("Total files found: {}", fileList.size());
        if (files != null) {
            for (int i = 0; i < files.size(); i++) {
                File f = files.get(i);
                log.info("  [{}] id={}, name=\"{}\", mimeType={}, size={}, modified={}",
                        i + 1, f.getId(), f.getName(), f.getMimeType(),
                        f.getSize() != null ? f.getSize() + " bytes" : "N/A",
                        f.getModifiedTime());
                if (f.getWebViewLink() != null) {
                    log.info("       webViewLink={}", f.getWebViewLink());
                }
            }
        }
        if (result.getNextPageToken() != null) {
            log.info("Next page token: {}", result.getNextPageToken());
        }
        log.info("---------- Timing ----------");
        log.info("API call duration: {} ms ({} seconds)", String.format("%.2f", elapsedMs), String.format("%.3f", elapsedSec));
        log.info("========== GDrive Search END ==========");
    }

    private String buildQuery() {
        List<String> conditions = new ArrayList<>();

        String rawQuery = getStringInput(INPUT_QUERY);
        if (rawQuery != null && !rawQuery.isBlank()) {
            return rawQuery; // Use raw query directly
        }

        String nameContains = getStringInput(INPUT_NAME_CONTAINS);
        if (nameContains != null && !nameContains.isBlank()) {
            conditions.add("name contains '" + escapeQueryValue(nameContains) + "'");
        }

        String mimeType = getStringInput(INPUT_MIME_TYPE);
        if (mimeType != null && !mimeType.isBlank()) {
            conditions.add("mimeType = '" + escapeQueryValue(mimeType) + "'");
        }

        String parentFolderId = getStringInput(INPUT_PARENT_FOLDER_ID);
        if (parentFolderId != null && !parentFolderId.isBlank()) {
            conditions.add("'" + escapeQueryValue(parentFolderId) + "' in parents");
        }

        String fullTextSearch = getStringInput(INPUT_FULL_TEXT_SEARCH);
        if (fullTextSearch != null && !fullTextSearch.isBlank()) {
            conditions.add("fullText contains '" + escapeQueryValue(fullTextSearch) + "'");
        }

        String modifiedAfter = getStringInput(INPUT_MODIFIED_AFTER);
        if (modifiedAfter != null && !modifiedAfter.isBlank()) {
            conditions.add("modifiedTime > '" + modifiedAfter + "'");
        }

        String modifiedBefore = getStringInput(INPUT_MODIFIED_BEFORE);
        if (modifiedBefore != null && !modifiedBefore.isBlank()) {
            conditions.add("modifiedTime < '" + modifiedBefore + "'");
        }

        boolean includeTrashed = getBooleanInputOrDefault(INPUT_INCLUDE_TRASHED, false);
        if (!includeTrashed) {
            conditions.add("trashed = false");
        }

        return String.join(" and ", conditions);
    }

    private String escapeQueryValue(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }
}
