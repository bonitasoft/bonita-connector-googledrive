package com.bonitasoft.connectors.gdrive;

import com.bonitasoft.test.toolkit.BonitaTestToolkit;
import com.bonitasoft.test.toolkit.BonitaTestToolkitFactory;
import com.bonitasoft.test.toolkit.model.ProcessDefinition;
import com.bonitasoft.test.toolkit.model.ProcessInstance;
import com.bonitasoft.test.toolkit.model.User;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static com.bonitasoft.test.toolkit.predicate.ProcessInstancePredicates.processInstanceCompleted;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Level 5: Bonita Test Toolkit integration test.
 *
 * <p>Connects to a Bonita Runtime where the GDrive connector test process
 * is deployed and validates end-to-end execution against real Google Drive API.</p>
 *
 * <h3>Two modes:</h3>
 * <dl>
 *   <dt>CI (git): {@code mvn clean install}</dt>
 *   <dd>This test is NOT compiled (excluded by maven-compiler-plugin without -P toolkit).
 *       Build always succeeds with zero external dependencies.</dd>
 *
 *   <dt>Local: {@code mvn verify -P toolkit}</dt>
 *   <dd>Connects to a running Bonita Studio Runtime with the process already deployed.
 *       If the process is not deployed, tests are SKIPPED (not failed).</dd>
 * </dl>
 *
 * <h3>One-time local setup:</h3>
 * <ol>
 *   <li>Build connector: {@code mvn clean install -DskipTests}</li>
 *   <li>Open Bonita Studio → Extensions → Import → select {@code bonita-connector-gdrive-all-*-bonita.jar}</li>
 *   <li>Open diagram {@code GDriveConnectorTestProcess-1.0.proc}</li>
 *   <li>Click <b>Run</b> (green play button) — this deploys with connector implementations resolved</li>
 *   <li>Run: {@code mvn verify -pl bonita-connector-gdrive-all -P toolkit}</li>
 * </ol>
 *
 * <h3>Configuration (overridable via -D):</h3>
 * <table>
 *   <tr><td>{@code bonita.url}</td><td>Runtime URL (default: http://localhost:8080/bonita)</td></tr>
 *   <tr><td>{@code bonita.tech.user}</td><td>Technical user (default: install)</td></tr>
 *   <tr><td>{@code bonita.tech.password}</td><td>Technical password (default: install)</td></tr>
 *   <tr><td>{@code gdrive.process.version}</td><td>Process version (default: 1.0)</td></tr>
 * </table>
 */
@Tag("btt")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("GDrive Connector - Bonita Process Toolkit IT")
class GDriveProcessToolkitIT {

    private static final Logger logger = LoggerFactory.getLogger(GDriveProcessToolkitIT.class);

    private static final Duration PROCESS_TIMEOUT = Duration.ofSeconds(120);
    private static final Duration POLL_INTERVAL = Duration.ofMillis(1000);

    private static final String PROCESS_NAME = "GDrive Connector Test";
    private static final String PROCESS_VERSION =
            System.getProperty("gdrive.process.version", "1.0");

    private BonitaTestToolkit toolkit;
    private ProcessDefinition processDefinition;
    private User initiator;

    @BeforeAll
    void setUp() {
        try {
            logger.info("Connecting to Bonita Runtime...");
            toolkit = BonitaTestToolkitFactory.INSTANCE.get(GDriveProcessToolkitIT.class);
            logger.info("Looking for process '{}' v{}", PROCESS_NAME, PROCESS_VERSION);
            processDefinition = toolkit.getProcessDefinition(PROCESS_NAME, PROCESS_VERSION);
        } catch (Exception e) {
            logger.warn("BTT setup failed — skipping all tests: {}", e.getMessage());
            Assumptions.assumeTrue(false,
                    "BTT not available: " + e.getMessage()
                    + ". See GDriveProcessToolkitIT javadoc for local setup.");
            return;
        }

        initiator = processDefinition.getInitiators().stream().findFirst().orElse(null);
        Assumptions.assumeTrue(initiator != null, "No initiator — check actor mapping");

        logger.info("Ready: {} v{}, Initiator: {}",
                processDefinition.getName(), processDefinition.getVersion(), initiator);
    }

    @Test
    @Order(1)
    @DisplayName("should_complete_full_lifecycle_when_valid_credentials")
    void should_complete_full_lifecycle_when_valid_credentials() {
        ProcessInstance instance = processDefinition.startProcessFor(initiator);
        logger.info("Started instance: {}", instance.getId());

        await().atMost(PROCESS_TIMEOUT)
                .pollInterval(POLL_INTERVAL)
                .until(instance, processInstanceCompleted());

        assertThat(instance.getNumberOfFailedFlowNodes())
                .as("All 10 service tasks (CreateFolder → Upload → Search → Download → "
                        + "Copy → CreateArchiveFolder → Move → UploadConvert → Export → Delete) "
                        + "should complete without errors")
                .isZero();

        logger.info("Instance {} completed OK", instance.getId());
    }

    @Test
    @Order(2)
    @DisplayName("should_complete_within_acceptable_time")
    void should_complete_within_acceptable_time() {
        long t0 = System.currentTimeMillis();

        ProcessInstance instance = processDefinition.startProcessFor(initiator);
        logger.info("Started instance: {}", instance.getId());

        await().atMost(PROCESS_TIMEOUT)
                .pollInterval(POLL_INTERVAL)
                .until(instance, processInstanceCompleted());

        long elapsed = System.currentTimeMillis() - t0;
        logger.info("Completed in {} ms", elapsed);

        assertThat(elapsed).as("10 API calls should finish within 90s").isLessThan(90_000L);
        assertThat(instance.getNumberOfFailedFlowNodes()).isZero();
    }

    @Test
    @Order(3)
    @DisplayName("should_handle_concurrent_instances")
    void should_handle_concurrent_instances() {
        int n = 3;
        List<ProcessInstance> instances = new ArrayList<>();

        long t0 = System.currentTimeMillis();
        for (int i = 0; i < n; i++) {
            instances.add(processDefinition.startProcessFor(initiator));
            logger.info("Started concurrent instance {}", i + 1);
        }

        for (int i = 0; i < n; i++) {
            await().atMost(Duration.ofSeconds(180))
                    .pollInterval(POLL_INTERVAL)
                    .until(instances.get(i), processInstanceCompleted());
        }

        long elapsed = System.currentTimeMillis() - t0;
        logger.info("{} instances in {} ms (avg {} ms)", n, elapsed, elapsed / n);

        for (int i = 0; i < n; i++) {
            assertThat(instances.get(i).getNumberOfFailedFlowNodes())
                    .as("Instance %d: no failures", i + 1).isZero();
        }
    }
}
