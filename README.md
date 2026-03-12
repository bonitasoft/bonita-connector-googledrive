# Google Drive Connector

A multi-connector project for the Bonita platform to interact with Google Drive from a process.

[![Build](https://github.com/bonitasoft/bonita-connector-googledrive/actions/workflows/build.yml/badge.svg)](https://github.com/bonitasoft/bonita-connector-googledrive/actions/workflows/build.yml)
[![GitHub release](https://img.shields.io/github/v/release/bonitasoft/bonita-connector-googledrive?color=blue&label=Release)](https://github.com/bonitasoft/bonita-connector-googledrive/releases)
[![License: GPL v2](https://img.shields.io/badge/License-GPL%20v2-yellow.svg)](https://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html)

---

## Available Connectors

This project builds **7 distinct connector packages** from one unified codebase:

| Connector | Definition ID | Purpose |
|-----------|--------------|---------|
| **Upload File** | `gdrive-upload-file` | Upload a file to Google Drive |
| **Download File** | `gdrive-download-file` | Download a file from Google Drive |
| **Create Folder** | `gdrive-create-folder` | Create a folder in Google Drive |
| **Get File** | `gdrive-get-file` | Retrieve file metadata |
| **List Files** | `gdrive-list-files` | List files and folders with filtering |
| **Move File** | `gdrive-move-file` | Move or rename a file |
| **Delete File** | `gdrive-delete-file` | Trash or permanently delete a file |

## Features

### Authentication
- **Service Account** - JSON key-based authentication
- **Domain-Wide Delegation** - Impersonate Google Workspace users via service account
- **Application Default Credentials** - Fallback to ADC when no key is provided

### Reliability
- **Exponential backoff** - Automatic retry with jitter on rate limits and transient errors (429, 500, 502, 503, 504)
- **Configurable retries** - Up to 5 retries with 64s cap by default
- **Idempotent deletes** - Delete returns success on 404

### Upload
- **Simple upload** - Direct upload for small files
- **Resumable upload** - Automatic chunked upload for large files
- **Auto strategy** - Automatic selection based on file size

## Build

### Requirements
- **Java:** 17+
- **Maven:** 3.8+ (wrapper included)
- **Target Bonita:** 10.2.0

__Clone__ or __fork__ this repository, then at the root of the project run:

`./mvnw`

### Run unit and property tests only

`./mvnw clean verify`

### Run integration tests

Integration tests require a Google Cloud service account with domain-wide delegation.

Set the following environment variables:

```
GDRIVE_SERVICE_ACCOUNT_KEY_JSON=<service account key JSON, single line>
GDRIVE_IMPERSONATED_USER_EMAIL=<Google Workspace user email>
```

Then run:

`./mvnw clean verify -Pintegration-tests -Dsurefire.forkCount=0`

## Release

In order to create a new release:
- On the release branch, make sure to update the pom version (remove the -SNAPSHOT)
- Run the release action, set the version to release as parameter
- Update the `master` with the next SNAPSHOT version.

Once this is done, update the [Bonita marketplace repository](https://github.com/bonitasoft/bonita-marketplace) with the new version of the connector.

## Contributing

We would love you to contribute, pull requests are welcome! Please see the [CONTRIBUTING.md](CONTRIBUTING.md) for more information.

## License

The sources and documentation in this project are released under the [GPLv2 License](LICENSE)
