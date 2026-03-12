# SKILL: Bonita BPM Connector Generator

> **Purpose:** Generate a complete, production-ready Bonita BPM connector Maven project from an intermediate connector specification document.
>
> **Trigger:** A developer provides a structured Markdown spec and asks you to generate the connector implementation.
>
> **Output:** A local, ready-to-commit single-module Maven project with all source, tests, XML descriptors, and a generation report.
>
> **Templates:** All file templates are in the `resources/` directory next to this file. Read each template, replace placeholders, customize per the embedded instructions, and write to the target location.

---

## Quick-Reference Checklist

```
[ ] 1. Parse the spec — extract name, operations, parameters, outputs, auth, errors, rate limits
[ ] 2. Scaffold the project directory tree (single module)
[ ] 3. Generate POM                     <- resources/pom.xml.template
[ ] 4. Generate shared classes:
    [ ] 4a. AbstractConnector          <- resources/AbstractConnector.java.template
    [ ] 4b. Configuration              <- resources/Configuration.java.template
    [ ] 4c. Exception                  <- resources/Exception.java.template
    [ ] 4d. Client                     <- resources/Client.java.template
    [ ] 4e. RetryPolicy                <- resources/RetryPolicy.java.template
    [ ] 4f. Model records              <- resources/ModelRecord.java.template (one per operation)
[ ] 5. For EACH operation, generate:
    [ ] 5a. OperationConnector.java    <- resources/OperationConnector.java.template
    [ ] 5b. .def XML                   <- resources/connector-definition.def.template
    [ ] 5c. .impl XML                  <- resources/connector-implementation.impl.template
    [ ] 5d. .properties                <- resources/connector.properties.template
    [ ] 5e. Unit test                  <- resources/UnitTest.java.template
    [ ] 5f. Property test              <- resources/PropertyTest.java.template
    [ ] 5g. Integration test           <- resources/IntegrationTest.java.template
[ ] 6. Generate shared files (one copy each, NOT per-operation):
    [ ] 6a. Assembly descriptor        <- resources/connector-assembly.xml.template
    [ ] 6b. Groovy dep script          <- resources/dependencies-as-var.groovy.template
[ ] 7. Run `mvn verify` — fix ALL compilation/test failures until green
[ ] 8. Run `mvn install` — installs to local Maven repo (required for Bonita Studio import)
[ ] 9. Generate report                 <- resources/GENERATION_REPORT.md.template
```

---

## Step 0 — Prerequisites

Verify before starting:
- Java 17+ (`java --version`)
- Maven 3.8+ (`mvn --version`)
- Writable working directory

Stop and inform the user if anything is missing.

---

## Step 1 — Parse the Spec

Read the provided spec document. Extract and memorize:

| Data Point | Where in Spec |
|---|---|
| **Connector name** | Executive Summary / title. Use lowercase-kebab-case. |
| **Display name** | Human-readable name for UI (e.g., "Google Drive", "Slack") |
| **Operations** | Scope matrix table — each row is an operation |
| **Per-operation parameters** | Connector Definitions — input tables: Parameter, Type, Mandatory, Default, Scope, Widget, Description |
| **Per-operation outputs** | Connector Definitions — output tables: Parameter, Type, Description |
| **Auth pattern** | Authentication section — modes, connection params with scope |
| **Error handling table** | Architecture section — HTTP codes to actions (retry/fail/log) |
| **Rate limits** | Architecture section — requests/second or requests/minute |
| **Maven dependencies** | Architecture section — API client libraries, versions |

### Naming Conventions

| Concept | Convention | Example (name="gdrive") |
|---|---|---|
| Project root dir | `bonita-connector-{name}` | `bonita-connector-gdrive` |
| Java package | `com.bonitasoft.connectors.{name}` | `com.bonitasoft.connectors.gdrive` |
| Abstract class | `Abstract{Name}Connector` | `AbstractGDriveConnector` |
| Config class | `{Name}Configuration` | `GDriveConfiguration` |
| Exception class | `{Name}Exception` | `GDriveException` |
| Client class | `{Name}Client` | `GDriveClient` |
| Operation class | `{Operation}Connector` | `GetFileConnector` |
| Maven groupId | `org.bonitasoft.connectors` | `org.bonitasoft.connectors` |

`{Name}` / `{Operation}` = PascalCase. `{name}` / `{operation}` = lowercase (hyphens in dirs/files, camelCase in Java).

---

## Step 2 — Scaffold the Directory Tree

**CRITICAL: Single-module project. NOT multi-module.** All operations, shared classes, and resources go into ONE module.

```
bonita-connector-{name}/
├── pom.xml
├── src/
│   ├── assembly/
│   │   └── all-assembly.xml
│   ├── script/
│   │   └── dependencies-as-var.groovy
│   ├── main/
│   │   ├── java/com/bonitasoft/connectors/{name}/
│   │   │   ├── Abstract{Name}Connector.java
│   │   │   ├── {Name}Client.java
│   │   │   ├── {Name}Configuration.java
│   │   │   ├── {Name}Exception.java
│   │   │   ├── RetryPolicy.java
│   │   │   ├── {Operation1}Connector.java
│   │   │   ├── {Operation2}Connector.java
│   │   │   └── ...
│   │   ├── resources-filtered/          (Maven-filtered at build time)
│   │   │   ├── {name}-{operation1}.def
│   │   │   ├── {name}-{operation1}.impl
│   │   │   ├── {name}-{operation2}.def
│   │   │   ├── {name}-{operation2}.impl
│   │   │   └── ...
│   │   └── resources/                   (NOT filtered)
│   │       ├── {name}-{operation1}.properties
│   │       ├── {name}-{operation2}.properties
│   │       ├── {icon-filename}.png
│   │       └── ...
│   └── test/java/com/bonitasoft/connectors/{name}/
│       ├── {Operation1}ConnectorTest.java
│       ├── {Operation1}ConnectorPropertyTest.java
│       ├── {Operation1}ConnectorIntegrationTest.java
│       └── ...
```

**Icon:** Must be **16x16 pixels** PNG. Bonita Studio renders it inline in the connector picker — larger icons break the layout.

---

## Steps 3–5 — Generate Files from Templates

For each file to generate:
1. **Read** the corresponding template from `resources/`.
2. **Replace placeholders** (`{name}`, `{Name}`, `{operation}`, `{Operation}`, etc.).
3. **Follow the CUSTOMIZATION instructions** embedded in each template's header comment.
4. **Write** the result to the target path in the project structure.

### Template Manifest

| Target File | Template | Key Customization |
|---|---|---|
| `pom.xml` | `pom.xml.template` | Add per-operation properties, API client deps |
| `Abstract{Name}Connector.java` | `AbstractConnector.java.template` | Add auth validation logic |
| `{Name}Configuration.java` | `Configuration.java.template` | Add all fields from spec param tables |
| `{Name}Exception.java` | `Exception.java.template` | None — use as-is |
| `{Name}Client.java` | `Client.java.template` | Add one method per operation |
| `RetryPolicy.java` | `RetryPolicy.java.template` | None — use as-is |
| `model/{Operation}Result.java` | `ModelRecord.java.template` | Add output fields from spec |
| `{Operation}Connector.java` | `OperationConnector.java.template` | Add input/output constants, implement methods |
| `{name}-{operation}.def` | `connector-definition.def.template` | Add all inputs, outputs, pages, widgets |
| `{name}-{operation}.impl` | `connector-implementation.impl.template` | None — jarDependencies auto-generated |
| `{name}-{operation}.properties` | `connector.properties.template` | Add i18n label for every widget |
| `src/assembly/all-assembly.xml` | `connector-assembly.xml.template` | None — copy as-is (ONE file, not per-operation) |
| `src/script/dependencies-as-var.groovy` | `dependencies-as-var.groovy.template` | None — copy as-is (ONE file) |
| `{Operation}ConnectorTest.java` | `UnitTest.java.template` | **Implement ALL test methods** (see below) |
| `{Operation}ConnectorPropertyTest.java` | `PropertyTest.java.template` | **Add 10+ jqwik properties** |
| `{Operation}ConnectorIntegrationTest.java` | `IntegrationTest.java.template` | Set correct env var names |
| `GENERATION_REPORT.md` | `GENERATION_REPORT.md.template` | Fill in actual values after build |

### Critical: Version and Scope Rules

| Rule | Details |
|---|---|
| **Version must be release** | Use `1.0.0`, NEVER `1.0.0-SNAPSHOT`. SNAPSHOTs cause Bonita Studio import failures because Maven tries to resolve them from remote repos. |
| **All deps `provided` scope** | ALL dependencies (including API client libraries) must be `provided`. Bonita's analyze plugin tries to resolve compile/runtime deps from remote repos and fails. The groovy script and assembly handle runtime bundling separately. |
| **groupId = `org.bonitasoft.connectors`** | Matches the convention of `bonita-connector-rest` and `bonita-connector-ai`. |

### Critical: .def Page Grouping Rules

The spec's **Scope column** drives which UI page each input appears on:

| Scope in Spec | Page ID | Purpose |
|---|---|---|
| Project / Runtime | `connectionPage` | Auth & connection settings |
| Connector | `operationPage` | Main operation inputs |
| Process | `configurationPage` | Process-level config |
| _(advanced flags)_ | `advancedPage` | Timeouts, retries, debug |

### Critical: Widget Conventions

Map the spec's **Widget column** to the .def `xsi:type`:

| Spec Widget | xsi:type |
|---|---|
| Text | `definition:Text` |
| TextArea | `definition:TextArea` |
| Password | `definition:Password` |
| Checkbox | `definition:Checkbox` |
| RadioGroup | `definition:RadioGroup` (must have `<choices>` children) |
| Select | `definition:Select` (must have `<items>` children) |
| Array | `definition:Array` |

Widget IDs follow the convention `{inputName}Widget` (e.g., `fileIdWidget`, `apiKeyWidget`).

### Critical: .properties Key Naming

| Element | Key Pattern | Example |
|---|---|---|
| Page title | `{pageId}.pageTitle` | `connectionPage.pageTitle=Connection` |
| Page description | `{pageId}.pageDescription` | `connectionPage.pageDescription=Auth settings` |
| Widget label | `{widgetId}.label` | `fileIdWidget.label=File ID` |
| Widget description | `{widgetId}.description` | `fileIdWidget.description=The Google Drive file ID` |
| Category | `{categoryId}.category` | `Google Drive.category=Google Drive` |
| Connector label | `connectorDefinitionLabel` | `connectorDefinitionLabel=Google Drive - Get File` |
| Connector description | `connectorDefinitionDescription` | `connectorDefinitionDescription=Retrieves file metadata` |

### Critical: .impl Element Order

Elements MUST follow this exact XSD sequence order:
1. `implementationId`
2. `implementationVersion`
3. `definitionId`
4. `definitionVersion`
5. `implementationClassname`
6. `hasSources` (always `false`)
7. `description`
8. `jarDependencies` (via `${connector-dependencies}`)

### Critical: Tests Must Be Fully Implemented

The template files contain TODO stubs for guidance. You MUST replace every TODO with real, working test code. **Do NOT leave any test method as a stub.**

**Unit test required scenarios (minimum 8 per operation):**
1. Happy path — mock client success, verify all outputs
2. Each mandatory input missing — one test per param, verify `ConnectorValidationException`
3. Rate limit retry — mock 429 then success
4. Auth failure — mock 401/403, immediate failure
5. Network timeout — mock timeout
6. Null optional inputs — verify defaults
7. All output fields populated
8. Error path — verify `success=false` + `errorMessage`

**Mock client injection pattern** (since `connect()` creates the client):
```java
var clientField = Abstract{Name}Connector.class.getDeclaredField("client");
clientField.setAccessible(true);
clientField.set(connector, mockClient);
```

**Property tests:** Minimum 10 jqwik `@Property` methods per operation.

---

## Step 6 — Build and Fix

```bash
cd bonita-connector-{name}
mvn clean verify 2>&1
```

Iterate until the build is green. Do NOT give up.

**Common fixes:**

| Issue | Fix |
|---|---|
| `bonita-common` not found | Add Bonitasoft repo to POM `<repositories>` |
| Lombok not processing | Verify `annotationProcessorPaths` in compiler plugin |
| Resources not filtered | Verify `<filtering>true</filtering>` for `resources-filtered` |
| Test can't access private field | Use reflection to inject mock client |
| Pitest below 95% | Add test cases targeting surviving mutants |

---

## Step 7 — Install to Local Repo

```bash
mvn install -DskipTests
```

**This is mandatory.** Bonita Studio's "Import from file" needs the POM in the local Maven repo to analyze dependencies.

---

## Step 8 — Mutation Testing

```bash
mvn org.pitest:pitest-maven:mutationCoverage
```

If below 95%, review `target/pit-reports/index.html` and add targeted tests.

---

## Step 9 — Generation Report

Read `resources/GENERATION_REPORT.md.template`, fill in actual values from the build, write to project root as `GENERATION_REPORT.md`.

---

## Quality Gates — Enforced on ALL Generated Code

| Rule | Check |
|---|---|
| No `System.out.println` | Use `@Slf4j` + `log.info/debug/warn/error` only |
| No raw `Exception` catches | Catch specific exceptions, wrap in `{Name}Exception` |
| All outputs always set | `success` + `errorMessage` on every path (success AND failure) |
| No hardcoded credentials | Secrets from input parameters or env vars only |
| Integration tests skippable | All use `@EnabledIfEnvironmentVariable` |
| TODOs clearly marked | Prefix: `// TODO [CONNECTOR-GEN]:` |
| Java 17 features | Records for models, text blocks where helpful |
| Immutable models | All model classes are records |
| Thread safety | RetryPolicy and Client safe for single-instance use |
| Icon size | 16x16 pixels PNG |

---

## What NOT to Do

- Do NOT push to GitHub or any remote.
- Do NOT update any marketplace or registry.
- Do NOT run deployment steps.
- Do NOT create Dockerfiles or CI pipelines unless the spec explicitly requests them.
- Do NOT add features not in the spec.
- Do NOT use multi-module Maven structure (single-module only).
- Do NOT use SNAPSHOT versions (release versions only).
- Do NOT use `compile` or `runtime` scope for dependencies (use `provided` for non-test deps).

---

## Troubleshooting

**"Cannot resolve org.bonitasoft.engine:bonita-common"**
Add to POM:
```xml
<repositories>
    <repository>
        <id>bonitasoft-releases</id>
        <url>https://bonitasoft.jfrog.io/artifactory/maven</url>
    </repository>
</repositories>
```

**"Lombok annotations not processed"**
Ensure `maven-compiler-plugin` has `annotationProcessorPaths` with Lombok (already in POM template).

**"resources-filtered files not being filtered"**
Ensure POM has `<filtering>true</filtering>` for `src/main/resources-filtered`.

**"connector.setInputParameters() doesn't work"**
Bonita `AbstractConnector` stores inputs in an internal map. Use `connector.setInputParameters(Map.of("paramName", value))`. Ensure param names exactly match the constants.

**"definition found but no matching implementation"**
Check these in order:
1. `.impl` namespace must be `6.0` (not `6.1`)
2. `.impl` element order must match XSD sequence (see above)
3. `.impl` must include `<hasSources>false</hasSources>`
4. `implementationVersion` must match JAR version
5. Embedded POM must have no unresolvable compile/runtime dependencies
6. `jarDependencies` must include the connector's own JAR as first entry

**"Maven properties information are missing"**
Import the main JAR (built by `maven-jar-plugin`), NOT the assembly ZIP.

**"Failed to analyze artifact"**
1. Ensure version is NOT a SNAPSHOT
2. Ensure all deps are `provided` or `test` scope
3. Run `mvn install` before importing into Bonita Studio

---

## Completion

When done, inform the user:
1. Project location
2. Build command: `mvn clean verify`
3. Install command: `mvn install -DskipTests` (required before Bonita Studio import)
4. Integration test command: `mvn verify -Pintegration-tests` (after setting env vars)
5. Import into Bonita Studio: use `target/bonita-connector-{name}-{version}.jar` via "Import from file"
6. Point to `GENERATION_REPORT.md` for details and remaining TODOs
