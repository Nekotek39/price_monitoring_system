package pl.pricemonitor.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.pricemonitor.persistence.entity.SearchLog;

import java.time.Instant;
import java.util.List;

public interface SearchLogRepository extends JpaRepository<SearchLog, Long> {

    /** Products searched at least {@code threshold} times since {@code since}. */
    @Query(value = """
            SELECT w.produkt_id FROM wyszukiwanie w
             WHERE w.produkt_id IS NOT NULL AND w.utworzono >= :since
             GROUP BY w.produkt_id
            HAVING count(*) >= :threshold
            """, nativeQuery = true)
    List<Long> findPopularProductIds(@Param("since") Instant since, @Param("threshold") long threshold);

    long countByProduct_IdAndCreatedAtAfter(Long productId, Instant since);
}
