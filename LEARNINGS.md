# Learnings from GDrive Connector Build

Captured during the implementation of bonita-connector-googledrive.
These should be incorporated into the connector generator skill.

---

## 1. .impl XML Schema Must Be 6.0, Not 6.1

**Problem:** Bonita Studio rejects `.impl` files with namespace `http://www.bonitasoft.org/ns/connector/implementation/6.1`.
**Fix:** Use `6.0` in the namespace: `http://www.bonitasoft.org/ns/connector/implementation/6.0`.
**Note:** `.def` files correctly use `6.1` — only `.impl` requires `6.0`.

## 2. .impl Element Order Matters

**Problem:** Bonita Studio says "definition found but no matching implementation" when elements are in wrong order.
**Fix:** Elements MUST follow this exact order (matches the XSD sequence):
1. `implementationId`
2. `implementationVersion`
3. `definitionId`
4. `definitionVersion`
5. `implementationClassname`
6. `hasSources` (boolean — `false` for compiled connectors)
7. `description`
8. `jarDependencies` (or `${connector-dependencies}`)

**Reference:** Both `bonita-connector-rest` and `bonita-connector-ai` use this exact order.

## 3. .impl Should Include a `<description>` Element

**Problem:** Missing `<description>` element may cause validation issues.
**Fix:** Add `<description>Default ${connector-id.def.id} implementation</description>` after `implementationClassname`.

## 4. ZIP Assembly Packaging for Deployable Artifacts

**What:** Each operation module produces a self-contained archive with `.def`, `.impl`, `.properties`, icon, and all runtime JARs in `classpath/`.
**How:**
- `groovy-maven-plugin` runs `dependencies-as-var.groovy` at `generate-resources` to populate `${connector-dependencies}` in `.impl`
- `maven-assembly-plugin` reads `src/assembly/connector-assembly.xml` at `package` phase
- Both files are identical across all operation modules (copy as-is, no placeholders)

## 5. Assembly Format Should Be JAR, Not ZIP

**Problem:** Bonita Studio's "Import connector from file" dialog only accepts `.jar` files. A `.zip` is invisible in the file picker.
**Fix:** Use `<format>jar</format>` in the assembly descriptor instead of `<format>zip</format>`.

## 6. Bonita Studio Imports Require Maven Metadata

**Problem:** Importing the assembly JAR (`-all.jar`) fails with "Maven properties information are missing".
**Fix:** For local import, the customer should import the **regular module JAR** (built by `maven-jar-plugin`, contains `META-INF/maven/` metadata). Dependencies must be in the local Maven repo (`mvn install` first).
**For production distribution:** Publish to Maven Central so Bonita Studio resolves dependencies automatically via the Extension Manager / Marketplace.

## 7. slf4j-api Must Be Scope `provided`

**Why:** Bonita runtime already includes slf4j. Including it in the connector ZIP would cause classloading conflicts and bloats the archive.

## 8. bonita-common Must Be Scope `provided`

**Why:** Already present in the Bonita runtime. Same reasoning as slf4j.

## 9. Service Account Impersonation for Integration Tests

**Problem:** Google service accounts have no storage quota — they can't create files on their own Drive (`storageQuotaExceeded`).
**Fix:** Use domain-wide delegation with `impersonatedUserEmail`. Integration tests require:
- `GDRIVE_SERVICE_ACCOUNT_KEY_JSON` env var (single-line JSON)
- `GDRIVE_IMPERSONATED_USER_EMAIL` env var (Google Workspace user email)
- Service account must have domain-wide delegation enabled in Google Cloud Console
- Workspace admin must authorize the client ID with `https://www.googleapis.com/auth/drive` scope

## 10. `getOutputParameters()` Is Protected in AbstractConnector

**Problem:** `org.bonitasoft.engine.connector.AbstractConnector.getOutputParameters()` is `protected`. Tests in a different package can't access it.
**Fix:** Add a package-visible wrapper in the abstract connector:
```java
Map<String, Object> getOutputs() {
    return getOutputParameters();
}
```

## 11. Base64 Round-Trip Requires Explicit Charset

**Problem:** `content.getBytes()` uses platform default charset (Cp1252 on Windows), which can't round-trip arbitrary Unicode in property-based tests.
**Fix:** Always use `StandardCharsets.UTF_8` explicitly.

## 12. Surefire Forked JVM May Not See Env Vars with Multiline Values

**Problem:** `GDRIVE_SERVICE_ACCOUNT_KEY_JSON` contains multiline JSON. The forked surefire JVM doesn't receive it correctly.
**Fix:** Strip newlines from the JSON (`tr -d '\n' | tr -d '\r'`) and use `-Dsurefire.forkCount=0` to run tests in the same JVM.

## 13. Integration Tests Must Be Self-Contained per Module

**Problem:** Integration tests that reference connectors from other modules (e.g., download test using UploadFileConnector for setup) fail because cross-module classes aren't on the classpath.
**Fix:** Use `GDriveClient` from the core module for test setup/teardown instead of referencing other operation connectors.

## 14. Retry Test Logic: Mock Throws Directly

**Problem:** Test `shouldRetryOnRateLimit` expected retry-then-success, but retry logic is inside `GDriveClient` which is mocked. The mock throws the exception directly without retrying.
**Fix:** When client throws a retryable exception, the connector catches it and sets `success=false`. Test should assert failure, not success-after-retry.

## 15. Delete Returns Success on 404 (Idempotent)

**Design decision:** `GDriveClient.deleteFile()` treats 404 as success (file already gone). Tests should not assert `errorMessage` on success paths since it's only set on failure.

## 16. .impl Must Include `<hasSources>false</hasSources>`

**Problem:** "definition found but no matching implementation" when `.impl` is missing `<hasSources>`.
**Fix:** Add `<hasSources>false</hasSources>` after `<implementationClassname>` and before `<description>`. Both `bonita-connector-rest` and `bonita-connector-ai` include this element.

## 17. `implementationVersion` Must Track `${project.version}`

**Problem:** Hardcoding `implementationVersion` to `1.0.0` while the JAR is `1.0.0-SNAPSHOT` creates a mismatch.
**Fix:** Use `${project.version}` for `connector-id.impl.version` in each module POM. The REST and AI connectors both do this.

## 18. .def Category Must Define Its Parent

**Problem:** Using `parentCategoryId="Cloud"` without defining the `Cloud` category caused `UNKNOWN_DEFINITION_TYPE` warning.
**Fix:** Define the parent category first, then the child:
```xml
<category id="Cloud" icon="gdrive.png"/>
<category id="GoogleDrive" icon="gdrive.png" parentCategoryId="Cloud"/>
```
**Reference:** AI connector defines `<category id="AI" icon="AI.png"/>` before `<category id="OpenAI" parentCategoryId="AI"/>`.

## 19. .properties Key Naming Convention

**Problem:** Using `{inputName}.name` and `{pageId}.name` keys — Bonita Studio expects different keys.
**Fix:** Follow the convention from `bonita-connector-rest`:
- Page titles: `{pageId}.pageTitle` and `{pageId}.pageDescription`
- Widget labels: `{widgetId}.label` and `{widgetId}.description`
- Category label: `{categoryId}.category=Display Name`
- Connector: `connectorDefinitionLabel` and `connectorDefinitionDescription`

## 20. groupId Should Be `org.bonitasoft.connectors`

**Problem:** Using `com.bonitasoft.connectors` (enterprise convention) instead of `org.bonitasoft.connectors` (community/open-source).
**Fix:** Use `org.bonitasoft.connectors` as groupId — matching `bonita-connector-rest` and `bonita-connector-ai`.
**Note:** Java package (`com.bonitasoft.connectors.gdrive`) is independent from Maven groupId — no rename needed.

## 21. jarDependencies Must Include the Module's Own JAR

**Problem:** `.impl` jarDependencies did not list the connector's own JAR, causing Bonita to not find the implementation class.
**Fix:** `dependencies-as-var.groovy` must prepend `${mavenProject.build.finalName}.jar` as the first jarDependency entry.

## 22. .def Select/RadioGroup Widgets Need Child Elements

**Problem:** `<widget xsi:type="definition:Select">` without `<items>` children and `<widget xsi:type="definition:RadioGroup">` without `<choices>` children render empty in Bonita Studio.
**Fix:** Always include child `<items>` for Select and `<choices>` for RadioGroup widgets, or use `Text` type instead.

## 23. Single-Module Architecture Required — NOT Multi-Module

**Problem:** The skill generates a multi-module Maven project (parent POM + core module + one module per operation). Bonita Studio's "Import from file" reads the embedded `META-INF/maven/.../pom.xml` inside the JAR and tries to resolve all compile/runtime dependencies. In a multi-module setup, each operation JAR depends on `bonita-connector-{name}-core:SNAPSHOT` — a local module that is NOT available in any Maven repo. Bonita cannot resolve it and fails with "definition found but no matching implementation."

**Fix:** Generate a **single-module** project (like `bonita-connector-rest`). All operations, core classes, .def/.impl/.properties files go into ONE module that produces ONE JAR. No parent POM, no child modules, no cross-module dependencies.

**Why this works:** With a single module, the only compile dependencies are the external API libraries (e.g., Google Drive API) which are available in Maven Central. Bonita can resolve these, or simply doesn't need to (since all connector classes are already in the JAR).

**Reference:** `bonita-connector-rest` is the canonical example — single-module, all HTTP operations in one JAR.

## 24. Single-Module Project Structure

**The correct structure (replaces the multi-module layout):**
```
bonita-connector-{name}/
├── pom.xml                          (single POM, no parent, no modules)
├── src/
│   ├── assembly/
│   │   └── all-assembly.xml         (zip with .def/.impl/.properties + classpath JARs)
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
│   │   ├── resources-filtered/      (Maven-filtered: .def + .impl files)
│   │   │   ├── {name}-{op1}.def
│   │   │   ├── {name}-{op1}.impl
│   │   │   ├── {name}-{op2}.def
│   │   │   ├── {name}-{op2}.impl
│   │   │   └── ...
│   │   └── resources/               (unfiltered: .properties + icon)
│   │       ├── {name}-{op1}.properties
│   │       ├── {name}-{op2}.properties
│   │       ├── {name}.png
│   │       └── ...
│   └── test/java/com/bonitasoft/connectors/{name}/
│       ├── {Operation1}ConnectorTest.java
│       └── ...
```

## 25. Per-Operation Maven Properties (Single-Module Pattern)

**Problem:** Multi-module used a single set of `connector-id.def.id` etc. per module. Single-module needs per-operation properties since all operations share one POM.

**Fix:** Use prefixed properties per operation (matching `bonita-connector-rest` convention):
```xml
<properties>
    <!-- Get File Connector -->
    <get-file.def.id>gdrive-get-file</get-file.def.id>
    <get-file.def.version>1.0.0</get-file.def.version>
    <get-file.impl.id>${get-file.def.id}-impl</get-file.impl.id>
    <get-file.impl.version>${project.version}</get-file.impl.version>
    <get-file.main-class>com.bonitasoft.connectors.gdrive.GetFileConnector</get-file.main-class>

    <!-- Delete File Connector -->
    <delete-file.def.id>gdrive-delete-file</delete-file.def.id>
    ...
</properties>
```

Each `.def` references `${OPERATION.def.id}` and `${OPERATION.def.version}`.
Each `.impl` references `${OPERATION.impl.id}`, `${OPERATION.impl.version}`, `${OPERATION.def.id}`, `${OPERATION.def.version}`, and `${OPERATION.main-class}`.

## 26. Embedded POM Must Have No Unresolvable Dependencies

**Problem:** Bonita Studio's "Import from file" reads the POM embedded at `META-INF/maven/{groupId}/{artifactId}/pom.xml` inside the JAR. If it finds compile/runtime dependencies that aren't in a reachable Maven repo, import fails silently ("definition found but no matching implementation").

**What works:**
- `provided` and `test` scope dependencies → Bonita ignores them (like REST connector)
- Compile deps available in Maven Central → Bonita can resolve them (like Google Drive API)
- `SNAPSHOT` deps in local modules → **FAIL** (not in any remote repo)
- Parent POM reference to a local module → **may FAIL** if parent can't be resolved

**Fix for single-module:** No parent POM, no local module dependencies. External API deps (Maven Central) as compile scope are fine.

## 27. Shade Plugin + Antrun Workaround (Multi-Module Only — Avoid If Possible)

**Context:** If for some reason a multi-module layout is needed, this workaround makes Bonita import work:
1. `maven-shade-plugin` merges core module classes into each operation JAR and generates `dependency-reduced-pom.xml` (without the core dependency)
2. Shade `<filters>` must exclude `META-INF/maven/**` from the core artifact to prevent core's pom.xml (with unresolvable deps) from being embedded
3. `maven-antrun-plugin` runs AFTER shade in `package` phase to replace the JAR's embedded `pom.xml` with the dependency-reduced version

**This is complex and fragile. Single-module architecture (Learning #23) avoids all of this.**

## 28. Assembly Descriptor for Single-Module

**Pattern:** The assembly creates a zip for deployment, including all .def/.impl/.properties files and runtime JARs in a `classpath/` directory. Follow `bonita-connector-rest`'s `all-assembly.xml`:
```xml
<assembly ...>
    <id>all</id>
    <formats><format>zip</format></formats>
    <includeBaseDirectory>false</includeBaseDirectory>
    <fileSets>
        <fileSet>
            <outputDirectory/>
            <directory>target/classes</directory>
            <includes><include>*.*</include></includes>
        </fileSet>
    </fileSets>
    <dependencySets>
        <dependencySet>
            <unpack>false</unpack>
            <scope>runtime</scope>
            <outputDirectory>classpath</outputDirectory>
        </dependencySet>
    </dependencySets>
</assembly>
```

**For Bonita Studio import:** Users import the main JAR (`target/{artifactId}-{version}.jar`), NOT the assembly zip. The zip is for production deployment.

## 29. SNAPSHOT Versions Break Bonita Studio Import — Use Release Versions

**Problem:** Bonita Studio's analyze plugin always tries to resolve SNAPSHOT artifacts from remote Maven repos (JFrog, Maven Central). If the artifact isn't published remotely, import fails with "Failed to analyze artifact" even if the JAR was installed locally. Failed resolution attempts are cached by Maven, making retries fail too.

**Fix:** Use a release version (`1.0.0`, not `1.0.0-SNAPSHOT`). Maven won't check remote repos for release versions that already exist in the local repo.

**Workflow:** Always run `mvn install` before importing into Bonita Studio, so the JAR + POM are in `~/.m2/repository`.

## 30. All Dependencies Must Be `provided` Scope (Including External API Libraries)

**Problem:** Even with a single-module project, compile-scope dependencies cause Bonita's analyze plugin to try resolving their full transitive dependency tree from remote repos.

**Fix:** Make ALL dependencies `provided` scope — not just Bonita runtime libs, but also the external API libraries (e.g., Google Drive API). The `provided` scope still allows compilation but prevents Bonita from trying to resolve them.

**jarDependencies still works:** Update the groovy script to include `provided`-scope deps in jarDependencies, excluding known Bonita runtime groupIds (`org.bonitasoft.engine`, `org.projectlombok`, `org.slf4j`):
```groovy
def bonitaRuntime = ['org.bonitasoft.engine', 'org.projectlombok', 'org.slf4j'] as Set
project.artifacts
    .findAll { a -> (a.scope in ['compile','runtime','provided']) && !bonitaRuntime.contains(a.groupId) }
    .sort { a -> a.artifactId }
    .each { a -> jarDependency("${a.artifactId}-${a.version}.${a.type}") }
```

**Assembly also needs updating:** Change `<scope>runtime</scope>` to `<scope>provided</scope>` in the assembly descriptor so the zip still bundles the API JARs.

## 31. Connector Icon Must Be 16x16 PNG

**Problem:** Using a 48x48 or 96x96 icon makes the connector list in Bonita Studio unusable — icons fill the entire row.
**Fix:** Always resize the icon to 16x16 pixels (PNG, RGBA). This matches `bonita-connector-rest` (rest.png is 16x16).

## 32. Widget IDs Must Be Consistent Across All .def Files

**Problem:** Some .def files used `id="applicationName"` while others used `id="applicationNameWidget"` for the same logical widget. The `.properties` keys must match the widget ID, so inconsistent IDs cause missing labels.
**Fix:** Use `{inputName}Widget` as the widget ID convention (e.g., `applicationNameWidget`, `fileIdWidget`). Then `.properties` keys are `{widgetId}.label` and `{widgetId}.description`.

## 33. Connector Display Names Should Use Consistent Prefix

**Problem:** Some connectors showed "GDrive - X" and others "Google Drive - X" in Bonita Studio's connector picker.
**Fix:** Standardize `connectorDefinitionLabel` in all `.properties` files to use the same prefix (e.g., "Google Drive - Get File", "Google Drive - Upload File").

## 34. Category Should Be Flat (No Parent) With Descriptive Name

**Problem:** Using parent-child categories (`Cloud` → `GoogleDrive`) creates unnecessary nesting. Using `GoogleDrive` as ID shows as-is without spaces.
**Fix:** Use a single flat category with a human-readable ID: `<category icon="{icon}" id="Google Drive"/>`. The `.properties` should include `Google Drive.category=Google Drive`.

---

## TODO: Still Pending

- [ ] Publish to Maven Central for production distribution
- [ ] Register in bonita-marketplace repo
- [ ] Run integration tests with a properly delegated service account
- [ ] Update the connector generator skill to use single-module architecture (Learnings #23-28)
