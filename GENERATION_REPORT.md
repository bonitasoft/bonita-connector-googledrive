# Connector Generation Report

## Summary
- **Connector name:** gdrive (Google Drive)
- **Generated:** 2026-03-12
- **Operations:** 7
- **Total source files:** 77

## Operations Generated

| Operation | Module | .def | .impl | Unit Tests | Property Tests | Integration Test |
|---|---|---|---|---|---|---|
| upload-file | bonita-connector-gdrive-upload-file | Yes | Yes | 13 | 11 | 3 (WireMock) |
| download-file | bonita-connector-gdrive-download-file | Yes | Yes | 14 | 11 | 2 (skippable) |
| create-folder | bonita-connector-gdrive-create-folder | Yes | Yes | 15 | 11 | 3 (skippable) |
| get-file | bonita-connector-gdrive-get-file | Yes | Yes | 13 | 11 | 3 (skippable) |
| list-files | bonita-connector-gdrive-list-files | Yes | Yes | 12 | 12 | 4 (skippable) |
| move-file | bonita-connector-gdrive-move-file | Yes | Yes | 16 | 12 | 4 (skippable) |
| delete-file | bonita-connector-gdrive-delete-file | Yes | Yes | 14 | 13 | 2 (skippable) |

**Total tests: 199** (97 unit + 81 property + 21 integration)

## Core Module Files

| File | Purpose |
|---|---|
| AbstractGDriveConnector.java | Base class — auth validation, connect/disconnect, error handling template |
| GDriveConfiguration.java | @Data @Builder config with all parameters across all operations |
| GDriveClient.java | Google Drive API facade — 7 methods, retry policy, exception translation |
| GDriveException.java | Typed exception with HTTP status code and retryable flag |
| RetryPolicy.java | Exponential backoff with jitter (1s base, 64s cap, 5 max retries) |

## Architecture Decisions

| Decision | Rationale |
|---|---|
| Result records as inner classes of GDriveClient | Co-locates API response models with the client that produces them |
| Single GDriveConfiguration for all operations | Follows template pattern; builder defaults handle operation-specific fields |
| Package-visible `getOutputs()` accessor | Bonita's `getOutputParameters()` is protected; needed for test assertions |
| Integration tests use WireMock (upload) / @EnabledIfEnvironmentVariable (others) | Upload tests can be self-contained; others need real Google credentials |

## Assumptions Made

- Google Drive API version `v3-rev20260220-2.0.0` as specified in the spec
- `AUTO` upload strategy defaults to simple upload (content handled as byte array)
- 404 on delete returns `success = true` (idempotent, as specified)
- Export size limit check (10 MB) performed after download (Google API doesn't support pre-check)
- Service Account JSON key passed as full JSON string content, not file path

## Build Commands

```bash
# Compile and test
mvn clean verify

# Integration tests (requires Google service account credentials)
export GDRIVE_SERVICE_ACCOUNT_KEY_JSON='{"type":"service_account",...}'
mvn verify -Pintegration-tests
```

## TODOs Requiring Human Input

- [ ] Replace placeholder icon (gdrive.png) with actual connector icon (48x48 PNG)
- [ ] Set real Google service account credentials for integration tests
- [ ] Run Pitest mutation testing: `mvn org.pitest:pitest-maven:mutationCoverage`
- [ ] Review upload strategy selection (currently delegates to Google client library)
- [ ] Add Shared Drive support if needed (requires `supportsAllDrives=true` flag)
