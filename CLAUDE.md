# Bonita Google Drive Connector - Project Instructions

## Skill: Bonita Connector Expert

### Architecture
- Multi-module Maven project: parent POM + common + 8 operations + all aggregator
- Java 17 with Bonita Runtime 10.2.0
- Google Drive API v3 with Service Account / OAuth 2.0

### Build Commands
```bash
# Full build (produces -bonita.jar per module)
mvn clean install -DskipTests

# Single module (requires prior install of dependencies)
mvn clean package -DskipTests -pl bonita-connector-gdrive-upload -am

# Run unit tests (102 tests, run during mvn install)
mvn test

# Run integration tests (auto-skipped if SA not configured)
# ITs run during mvn install; they skip gracefully without SA
mvn verify -DGDRIVE_SA_JSON_PATH=/path/to/sa.json -DGDRIVE_SHARED_FOLDER_ID=folderId

# Verify JAR contents
jar tf bonita-connector-gdrive-all/target/bonita-connector-gdrive-all-*-bonita.jar | grep -E '\.(def|impl|properties|png)$'
```

### Import Protocol (Bonita Studio 2025.2+)
1. Build: `mvn clean install -DskipTests`
2. Locate: `bonita-connector-gdrive-all/target/bonita-connector-gdrive-all-*-bonita.jar`
3. Studio: Extensions > Import extension > select the `-bonita.jar`
4. Verify: connector appears under "Google Drive" category in task configuration

### Connector File Structure (per module)

```
src/main/resources/
  {id}.def              # EMF ConnectorDefinition XML (namespace 6.1)
  {id}.properties       # i18n labels (connectorDefinitionLabel, pageTitle, widget labels)
  {icon}.png            # 16x16 binary PNG

src/main/resources-filtered/
  {id}.impl             # Implementation descriptor (namespace 6.0, Maven-filtered)
```

### EMF .def Critical Rules
- Namespace: `http://www.bonitasoft.org/ns/connector/definition/6.1`
- NEVER use `<label>` or `<description>` child elements (causes eResource() null error)
- `inputName` MUST be an attribute on `<widget>`, never a child element
- `<category>` MUST be self-closing: `<category icon="x.png" id="y"/>`
- Widget types: `definition:Text`, `definition:Password`, `definition:TextArea`, `definition:Checkbox`, `definition:Array`, `definition:Select`

### .impl Critical Rules
- Namespace: `http://www.bonitasoft.org/ns/connector/implementation/6.0`
- `definitionId` and `definitionVersion` MUST match .def `<id>` and `<version>` exactly
- `${connector-dependencies}` placeholder resolved by Groovy script at build time
- MUST be in `src/main/resources-filtered/` (not `src/main/resources/`)

### Properties Key Convention
```properties
connectorDefinitionLabel=Display Name
connectorDefinitionDescription=Description
{categoryId}.category=Category Name
{pageId}.pageTitle=Page Title
{widgetId}.label=Widget Label
{widgetId}.description=Widget Help
```

### Java Lifecycle (4 phases)
1. `validateInputParameters()` - Validate all inputs, throw `ConnectorValidationException`
2. `connect()` - Initialize API client (GDriveClient)
3. `executeBusinessLogic()` - Call `executeOperation(Drive)`, set outputs
4. `disconnect()` - Close resources

### Shade Plugin (fat JAR production)
- `shadedArtifactAttached=true`, `shadedClassifierName=bonita`
- Excludes: `org.bonitasoft.engine:*`, `org.slf4j:*` (provided by runtime)
- `ServicesResourceTransformer` for merging META-INF/services

### Token Management
- Authentication is internal to the connector (not exposed to the process)
- Service Account JSON or OAuth credentials passed as connector inputs
- `GDriveClient` handles token refresh and retry logic internally
- Rate limit handling with exponential backoff built into `executeWithRetry()`

### Error Prevention Checklist (before marking done)
1. `mvn clean install -DskipTests` succeeds
2. `jar tf *-bonita.jar` contains .def, .impl, .properties, .png at root
3. .impl inside JAR has resolved `<jarDependencies>` (not `${connector-dependencies}`)
4. .def has NO `<label>`/`<description>` child elements
5. All widget `inputName` are attributes, not children
6. `<category>` is self-closing
7. Icon is valid 16x16 PNG binary
8. `definitionId` in .impl matches `<id>` in .def
9. `implementationClassname` exists as .class in the JAR
10. Tests pass: `mvn test`

### Quality Targets
- Line Coverage: 95% (JaCoCo)
- Branch Coverage: 90% (JaCoCo)
- Mutation Score: 95% (PiTest STRONGER)
- Test naming: `should_X_when_Y()`

### Module Dependencies
```
common (base classes, no .def)
  ├── upload    → common + Google Drive SDK
  ├── download  → common + Google Drive SDK
  ├── export    → common + Google Drive SDK
  ├── create-folder → common + Google Drive SDK
  ├── delete    → common + Google Drive SDK
  ├── move      → common + Google Drive SDK
  ├── copy      → common + Google Drive SDK
  ├── search    → common + Google Drive SDK
  └── all       → depends on ALL above (single fat JAR)
```
