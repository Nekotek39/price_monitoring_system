package pl.pricemonitor.fetcher.queue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pl.pricemonitor.persistence.entity.JobType;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs against a real PostgreSQL (Testcontainers); skipped automatically when Docker is not available. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {"pricemonitor.scheduling.enabled=false", "spring.flyway.enabled=true"})
@Transactional
class FetchJobQueueTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    FetchJobQueue queue;

    @Autowired
    JdbcTemplate jdbc;

    private long addJob(long shopId, String type, int priority) {
        return jdbc.queryForObject("INSERT INTO zadanie_pobrania (sklep_id, typ, priorytet, status) "
                + "VALUES (?, ?, ?, 'PENDING') RETURNING id", Long.class, shopId, type, priority);
    }

    private String status(long id) {
        return jdbc.queryForObject("SELECT status FROM zadanie_pobrania WHERE id = ?", String.class, id);
    }

    @Test
    void claimsHighestPriorityFirstAndMarksInProgress() {
        long importJob = addJob(1, "IMPORT", 1);
        long onDemand = addJob(2, "ON_DEMAND", 10);

        ClaimedJob first = queue.claimNext().orElseThrow();
        assertEquals(onDemand, first.id());
        assertEquals(JobType.ON_DEMAND, first.type());
        assertEquals(1, first.attempts());
        assertEquals("IN_PROGRESS", status(onDemand));

        assertEquals(importJob, queue.claimNext().orElseThrow().id());
        assertTrue(queue.claimNext().isEmpty());
    }

    @Test
    void skipsUnavailableShopsAndDelayedJobs() {
        jdbc.update("UPDATE sklep SET status = 'UNAVAILABLE', niedostepny_do = now() + interval '5 minutes' WHERE id = 3");
        addJob(3, "CYCLIC", 5);
        long delayed = addJob(4, "CYCLIC", 5);
        queue.retryLater(delayed, Instant.now().plus(Duration.ofMinutes(1)), "test");

        assertTrue(queue.claimNext().isEmpty());
    }

    @Test
    void completesFailsAndRequeuesJobs() {
        long a = addJob(1, "IMPORT", 1);
        long b = addJob(2, "IMPORT", 1);
        queue.claimNext();
        queue.claimNext();

        queue.complete(a);
        queue.fail(b, "x".repeat(2000));
        assertEquals("DONE", status(a));
        assertEquals("FAILED", status(b));
        assertEquals(Integer.valueOf(1000), jdbc.queryForObject("SELECT length(blad) FROM zadanie_pobrania WHERE id = ?", Integer.class, b));

        long c = addJob(5, "IMPORT", 1);
        Optional<ClaimedJob> claimed = queue.claimNext();
        assertEquals(c, claimed.orElseThrow().id());
        jdbc.update("UPDATE zadanie_pobrania SET rozpoczeto = now() - interval '10 minutes' WHERE id = ?", c);
        assertEquals(1, queue.requeueStale(Duration.ofMinutes(2)));
        assertEquals("PENDING", status(c));
    }
}
