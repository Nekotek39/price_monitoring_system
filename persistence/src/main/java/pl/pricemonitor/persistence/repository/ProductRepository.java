package pl.pricemonitor.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.pricemonitor.persistence.entity.Product;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * Full-text-like search: substring match, exact GTIN, or pg_trgm word similarity
     * (tolerates typos and different word order).
     */
    @Query(value = """
            SELECT p.* FROM produkt p
             WHERE p.nazwa ILIKE '%' || :q || '%'
                OR p.gtin = :q
                OR word_similarity(:q, p.nazwa) >= 0.5
             ORDER BY (p.nazwa ILIKE '%' || :q || '%') DESC, word_similarity(:q, p.nazwa) DESC, p.nazwa
             LIMIT :limit
            """, nativeQuery = true)
    List<Product> search(@Param("q") String query, @Param("limit") int limit);
}
