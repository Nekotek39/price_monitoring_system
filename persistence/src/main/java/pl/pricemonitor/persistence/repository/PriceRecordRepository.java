package pl.pricemonitor.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.pricemonitor.persistence.entity.PriceRecord;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PriceRecordRepository extends JpaRepository<PriceRecord, Long> {

    Optional<PriceRecord> findFirstByOffer_IdOrderByFetchedAtDesc(Long offerId);

    List<PriceRecord> findByOffer_Product_IdAndFetchedAtAfterOrderByFetchedAtAsc(Long productId, Instant since);

    /** Products whose prices in the given shop changed since the given time (written by one fetch job). */
    @Query(value = """
            SELECT DISTINCT o.produkt_id
              FROM cena c
              JOIN oferta o ON o.id = c.oferta_id
             WHERE o.sklep_id = :shopId AND c.pobrano >= :since AND o.produkt_id IS NOT NULL
            """, nativeQuery = true)
    List<Long> findProductIdsWithPriceChanges(@Param("shopId") Long shopId, @Param("since") Instant since);
}
