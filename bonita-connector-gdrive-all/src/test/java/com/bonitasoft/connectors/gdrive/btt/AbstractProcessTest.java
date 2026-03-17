package com.bonitasoft.connectors.gdrive.btt;

import java.time.Duration;

import com.bonitasoft.test.toolkit.BonitaTestToolkit;
import com.bonitasoft.test.toolkit.BonitaTestToolkitFactory;
import com.bonitasoft.test.toolkit.model.ProcessDefinition;
import com.bonitasoft.test.toolkit.model.ProcessInstance;
import com.bonitasoft.test.toolkit.model.User;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static com.bonitasoft.test.toolkit.predicate.ProcessInstancePredicates.containsPendingUserTasks;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Base class for Bonita Test Toolkit (BTT) integration tests using Testcontainers.
 *
 * <p>Manages the Bonita Docker container lifecycle and BTT toolkit initialization.
 * All BTT tests must extend this class.</p>
 *
 * <h3>Run with:</h3>
 * <pre>mvn verify -Pbtt -pl bonita-connector-gdrive-all -am</pre>
 */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractProcessTest {

    private static final Logger logger = LoggerFactory.getLogger(AbstractProcessTest.class);

    protected static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);
    protected static final Duration DEFAULT_POLL_INTERVAL = Duration.ofMillis(500);

    /** Bonita runtime container — shared across all tests in the class. */
    @Container
    protected static final GenericContainer<?> bonita = new GenericContainer<>("bonita:2025.2")
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/bonita").forStatusCode(200).withStartupTimeout(Duration.ofMinutes(3)));

    /** The BTT toolkit — initialized in {@code @BeforeAll}. */
    protected BonitaTestToolkit toolkit;

    /**
     * Returns the Bonita runtime URL based on the Testcontainers mapped port.
     */
    protected String getBonitaUrl() {
        return "http://" + bonita.getHost() + ":" + bonita.getMappedPort(8080) + "/bonita";
    }

    @BeforeAll
    void setUpToolkit() {
        String bonitaUrl = getBonitaUrl();
        logger.info("Initializing Bonita Test Toolkit against {}", bonitaUrl);
        System.setProperty("bonita.url", bonitaUrl);
        toolkit = BonitaTestToolkitFactory.INSTANCE.get(getClass());
        logger.info("BTT initialized successfully");
    }

    @AfterAll
    void tearDown() {
        logger.info("Cleaning up process instances");
        try {
            toolkit.deleteProcessInstances();
        } catch (Exception e) {
            logger.warn("Cleanup failed: {}", e.getMessage());
        }
    }

    /**
     * Gets a process definition that is already deployed in the runtime.
     */
    protected ProcessDefinition getProcess(String processName) {
        return toolkit.getProcessDefinition(processName);
    }

    /**
     * Gets a process definition by name and version.
     */
    protected ProcessDefinition getProcess(String processName, String version) {
        return toolkit.getProcessDefinition(processName, version);
    }

    /**
     * Gets a Bonita user by username.
     */
    protected User getUser(String username) {
        return toolkit.getUser(username);
    }

    /**
     * Waits for a pending user task on the given process instance.
     */
    protected void waitForTask(ProcessInstance instance, String taskName) {
        logger.debug("Waiting for task '{}' on instance {}", taskName, instance.getId());
        await().atMost(DEFAULT_TIMEOUT)
                .pollInterval(DEFAULT_POLL_INTERVAL)
                .until(instance, containsPendingUserTasks(taskName));
    }

    /**
     * Waits for a process instance to complete (archived state).
     */
    protected void waitForCompletion(ProcessInstance instance) {
        logger.debug("Waiting for completion of instance {}", instance.getId());
        await().atMost(DEFAULT_TIMEOUT)
                .pollInterval(DEFAULT_POLL_INTERVAL)
                .until(instance::isArchived);
    }

    /**
     * Waits for completion with a custom timeout.
     */
    protected void waitForCompletion(ProcessInstance instance, Duration timeout) {
        await().atMost(timeout)
                .pollInterval(DEFAULT_POLL_INTERVAL)
                .until(instance::isArchived);
    }

    /**
     * Asserts no failed flow nodes (connector/script failures) on the instance.
     */
    protected void assertNoFailedFlowNodes(ProcessInstance instance) {
        assertThat(instance.getNumberOfFailedFlowNodes())
                .as("Expected no failed flow nodes on instance %s", instance.getId())
                .isZero();
    }
}
