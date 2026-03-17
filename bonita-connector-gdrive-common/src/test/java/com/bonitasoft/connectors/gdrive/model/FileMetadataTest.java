package com.bonitasoft.connectors.gdrive.model;

import com.google.api.client.util.DateTime;
import com.google.api.services.drive.model.File;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FileMetadata")
class FileMetadataTest {

    @Nested
    @DisplayName("From Drive File")
    class FromDriveFile {

        @Test
        @DisplayName("should convert Drive File to FileMetadata")
        void shouldConvert() {
            File driveFile = new File();
            driveFile.setId("file-123");
            driveFile.setName("document.pdf");
            driveFile.setMimeType("application/pdf");
            driveFile.setSize(2048L);

            FileMetadata metadata = FileMetadata.fromDriveFile(driveFile);

            assertThat(metadata.getFileId()).isEqualTo("file-123");
            assertThat(metadata.getFileName()).isEqualTo("document.pdf");
            assertThat(metadata.getMimeType()).isEqualTo("application/pdf");
            assertThat(metadata.getSize()).isEqualTo(2048L);
        }

        @Test
        @DisplayName("should handle null input")
        void shouldHandleNull() {
            assertThat(FileMetadata.fromDriveFile(null)).isNull();
        }
    }

    @Nested
    @DisplayName("Type detection")
    class TypeDetection {

        @Test
        @DisplayName("should detect Google Docs")
        void shouldDetectGoogleDocs() {
            var metadata = FileMetadata.builder()
                .mimeType("application/vnd.google-apps.document")
                .build();

            assertThat(metadata.isGoogleWorkspaceDocument()).isTrue();
        }

        @Test
        @DisplayName("should detect folder")
        void shouldDetectFolder() {
            var metadata = FileMetadata.builder()
                .mimeType("application/vnd.google-apps.folder")
                .build();

            assertThat(metadata.isFolder()).isTrue();
        }

        @Test
        @DisplayName("should not detect PDF as Google Workspace")
        void shouldNotDetectPdf() {
            var metadata = FileMetadata.builder()
                .mimeType("application/pdf")
                .build();

            assertThat(metadata.isGoogleWorkspaceDocument()).isFalse();
        }
    }

    @Nested
    @DisplayName("To Map")
    class ToMap {

        @Test
        @DisplayName("should convert to map")
        void shouldConvertToMap() {
            var metadata = FileMetadata.builder()
                .fileId("123")
                .fileName("test.pdf")
                .mimeType("application/pdf")
                .size(1024L)
                .build();

            Map<String, Object> map = metadata.toMap();

            assertThat(map).containsEntry("fileId", "123");
            assertThat(map).containsEntry("fileName", "test.pdf");
            assertThat(map).containsEntry("size", 1024L);
        }
    }
}
