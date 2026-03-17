package com.bonitasoft.connectors.gdrive.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.bonitasoft.connectors.gdrive.GDriveClient;
import com.bonitasoft.connectors.gdrive.GDriveConfiguration;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.About;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Integration tests for GDrive Search connector against real Google Drive API.
 * <p>
 * Requires environment variable GDRIVE_SA_JSON_PATH pointing to a Service Account JSON file.
 * Run with: mvn test -pl bonita-connector-gdrive-search -Dtest=GDriveSearchConnectorIT -DGDRIVE_SA_JSON_PATH=path/to/sa.json
 */
@Tag("provider-api")
@DisplayName("GDrive Search - Integration Tests")
class GDriveSearchConnectorIT {

    private static final String SA_JSON_PATH = System.getProperty("GDRIVE_SA_JSON_PATH",
            "C:/BonitaStudioSubscription-2025.2-u3/bonitasoft-conectores-06171232f32e.json");

    private static String serviceAccountJson;
    private static GDriveClient client;
    private static Drive driveService;

    @BeforeAll
    static void setUp() throws Exception {
        Path path = Path.of(SA_JSON_PATH);
        if (!Files.exists(path)) {
            throw new IllegalStateException("Service Account JSON not found at: " + SA_JSON_PATH);
        }
        serviceAccountJson = Files.readString(path);

        GDriveConfiguration config = new GDriveConfiguration(
                serviceAccountJson,
                null, // impersonateUser
                null, // clientId
                null, // clientSecret
                null, // refreshToken
                "Bonita-GDrive-IT",
                List.of(DriveScopes.DRIVE),
                30_000,
                60_000
        );
        client = new GDriveClient(config);
        driveService = client.getDriveService();
    }

    @Nested
    @DisplayName("Connectivity")
    class Connectivity {

        @Test
        @DisplayName("should_connect_and_get_about_info")
        void should_connect_and_get_about_info() throws Exception {
            About about = driveService.about().get()
                    .setFields("user,storageQuota")
                    .execute();

            System.out.println("=== About ===");
            System.out.println("User: " + about.getUser().getEmailAddress());
            System.out.println("Display name: " + about.getUser().getDisplayName());
            System.out.println("Storage used: " + about.getStorageQuota().getUsage() + " bytes");

            assertThat(about.getUser().getEmailAddress()).contains("bonitasoft-conectores");
        }
    }

    @Nested
    @DisplayName("Search")
    class Search {

        @Test
        @DisplayName("should_list_all_accessible_files_without_filter")
        void should_list_all_accessible_files_without_filter() throws Exception {
            FileList result = driveService.files().list()
                    .setPageSize(100)
                    .setIncludeItemsFromAllDrives(true)
                    .setSupportsAllDrives(true)
                    .setCorpora("allDrives")
                    .setFields("nextPageToken,files(id,name,mimeType,size,parents,createdTime,modifiedTime,shared,owners)")
                    .execute();

            List<File> files = result.getFiles();
            System.out.println("=== ALL files accessible by SA (no filter) ===");
            System.out.println("Total: " + (files != null ? files.size() : 0));
            if (files != null) {
                for (int i = 0; i < files.size(); i++) {
                    File f = files.get(i);
                    System.out.printf("  [%d] id=%s, name=\"%s\", mimeType=%s, parents=%s, shared=%s%n",
                            i + 1, f.getId(), f.getName(), f.getMimeType(), f.getParents(), f.getShared());
                    if (f.getOwners() != null) {
                        f.getOwners().forEach(o -> System.out.printf("       owner=%s%n", o.getEmailAddress()));
                    }
                }
            }

            // We just log results - if 0, it means the SA has no access to any files
            assertThat(files).isNotNull();
        }

        @Test
        @DisplayName("should_list_files_in_user_corpus_only")
        void should_list_files_in_user_corpus_only() throws Exception {
            FileList result = driveService.files().list()
                    .setPageSize(100)
                    .setFields("nextPageToken,files(id,name,mimeType,parents,shared)")
                    .execute();

            List<File> files = result.getFiles();
            System.out.println("=== Files in 'user' corpus (default, no allDrives) ===");
            System.out.println("Total: " + (files != null ? files.size() : 0));
            if (files != null) {
                for (int i = 0; i < files.size(); i++) {
                    File f = files.get(i);
                    System.out.printf("  [%d] id=%s, name=\"%s\", mimeType=%s%n",
                            i + 1, f.getId(), f.getName(), f.getMimeType());
                }
            }
        }

        @Test
        @DisplayName("should_find_files_with_name_contains_Test")
        void should_find_files_with_name_contains_Test() throws Exception {
            FileList result = driveService.files().list()
                    .setQ("name contains 'Test' and trashed = false")
                    .setPageSize(100)
                    .setIncludeItemsFromAllDrives(true)
                    .setSupportsAllDrives(true)
                    .setCorpora("allDrives")
                    .setFields("nextPageToken,files(id,name,mimeType,size,webViewLink,parents,modifiedTime)")
                    .execute();

            List<File> files = result.getFiles();
            System.out.println("=== Files matching 'name contains Test' (allDrives) ===");
            System.out.println("Total: " + (files != null ? files.size() : 0));
            if (files != null) {
                for (int i = 0; i < files.size(); i++) {
                    File f = files.get(i);
                    System.out.printf("  [%d] id=%s, name=\"%s\", mimeType=%s, webViewLink=%s%n",
                            i + 1, f.getId(), f.getName(), f.getMimeType(), f.getWebViewLink());
                }
            }
        }

        @Test
        @DisplayName("should_find_files_with_name_contains_Test_using_drive_file_scope")
        void should_find_files_with_name_contains_Test_using_drive_file_scope() throws Exception {
            // Test with the OLD restrictive scope to prove it was the problem
            GDriveConfiguration restrictiveConfig = new GDriveConfiguration(
                    serviceAccountJson,
                    null, null, null, null,
                    "Bonita-GDrive-IT-Restrictive",
                    List.of(DriveScopes.DRIVE_FILE), // OLD scope
                    30_000, 60_000
            );
            try (GDriveClient restrictiveClient = new GDriveClient(restrictiveConfig)) {
                Drive restrictiveDrive = restrictiveClient.getDriveService();

                FileList result = restrictiveDrive.files().list()
                        .setPageSize(100)
                        .setIncludeItemsFromAllDrives(true)
                        .setSupportsAllDrives(true)
                        .setCorpora("allDrives")
                        .setFields("nextPageToken,files(id,name,mimeType)")
                        .execute();

                List<File> files = result.getFiles();
                System.out.println("=== Files with DRIVE_FILE scope (restrictive) ===");
                System.out.println("Total: " + (files != null ? files.size() : 0));
                if (files != null) {
                    files.forEach(f -> System.out.printf("  id=%s, name=\"%s\"%n", f.getId(), f.getName()));
                }

                // With DRIVE_FILE scope, shared files should NOT be visible
                System.out.println(">>> If this returns 0 but the DRIVE scope test returns >0, the scope was the problem");
            }
        }

        @Test
        @DisplayName("should_list_shared_drives")
        void should_list_shared_drives() throws Exception {
            var sharedDrives = driveService.drives().list()
                    .setPageSize(100)
                    .execute();

            System.out.println("=== Shared Drives ===");
            var drives = sharedDrives.getDrives();
            System.out.println("Total: " + (drives != null ? drives.size() : 0));
            if (drives != null) {
                drives.forEach(d -> System.out.printf("  id=%s, name=\"%s\"%n", d.getId(), d.getName()));
            }
        }
    }
}
