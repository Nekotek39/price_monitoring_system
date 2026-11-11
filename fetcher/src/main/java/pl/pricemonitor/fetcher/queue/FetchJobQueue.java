package pl.pricemonitor.fetcher.queue;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import pl.pricemonitor.persistence.entity.JobType;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Queue operations in plain SQL. {@code FOR UPDATE ... SKIP LOCKED} lets several fetcher instances take jobs
 * concurrently without taking the same job twice and without waiting for each other.
 */
@Component
public class FetchJobQueue {

    static final String CLAIM_SQL = """
            UPDATE zadanie_pobrania z
               SET status = 'IN_PROGRESS', rozpoczeto = now(), liczba_prob = z.liczba_prob + 1
             WHERE z.id = (SELECT j.id
                             FROM zadanie_pobrania j
                             JOIN sklep s ON s.id = j.sklep_id
                            WHERE j.status = 'PENDING'
                              AND j.nie_wczesniej_niz <= now()
                              AND (s.niedostepny_do IS NULL OR s.niedostepny_do <= now())
                            ORDER BY j.priorytet DESC, j.utworzono
                            LIMIT 1
                              FOR UPDATE OF j SKIP LOCKED)
            RETURNING z.id, z.sklep_id, z.oferta_id, z.produkt_id, z.typ, z.liczba_prob, z.rozpoczeto
            """;

    private static final RowMapper<ClaimedJob> MAPPER = (rs, i) -> new ClaimedJob(
            rs.getLong("id"),
            rs.getLong("sklep_id"),
            rs.getObject("oferta_id", Long.class),
            rs.getObject("produkt_id", Long.class),
            JobType.valueOf(rs.getString("typ")),
            rs.getInt("liczba_prob"),
            rs.getTimestamp("rozpoczeto").toInstant());

    private final JdbcTemplate jdbc;

    public FetchJobQueue(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Takes the next job (highest priority first) or returns empty when there is nothing to do. */
    public Optional<ClaimedJob> claimNext() {
        List<ClaimedJob> jobs = jdbc.query(CLAIM_SQL, MAPPER);
        return jobs.stream().findFirst();
    }

    public void complete(long jobId) {
        jdbc.update("UPDATE zadanie_pobrania SET status = 'DONE', blad = NULL, zakonczono = now() WHERE id = ?", jobId);
    }

    public void retryLater(long jobId, Instant notBefore, String error) {
        jdbc.update("UPDATE zadanie_pobrania SET status = 'PENDING', nie_wczesniej_niz = ?, blad = ? WHERE id = ?",
                Timestamp.from(notBefore), truncate(error), jobId);
    }

    public void fail(long jobId, String error) {
        jdbc.update("UPDATE zadanie_pobrania SET status = 'FAILED', blad = ?, zakonczono = now() WHERE id = ?",
                truncate(error), jobId);
    }

    /** Returns jobs of crashed workers to the queue. */
    public int requeueStale(Duration olderThan) {
        return jdbc.update("""
                UPDATE zadanie_pobrania SET status = 'PENDING', blad = 'Przerwane - ponowione'
                 WHERE status = 'IN_PROGRESS' AND rozpoczeto < now() - make_interval(secs => ?)
                """, olderThan.toSeconds());
    }

    private static String truncate(String error) {
        if (error == null) {
            return null;
        }
        return error.length() > 1000 ? error.substring(0, 1000) : error;
    }
}
