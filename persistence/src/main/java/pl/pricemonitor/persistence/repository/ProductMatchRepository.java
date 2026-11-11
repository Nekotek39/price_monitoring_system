package pl.pricemonitor.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.pricemonitor.core.matching.MatchStatus;
import pl.pricemonitor.persistence.entity.ProductMatch;

import java.util.List;
import java.util.Optional;

public interface ProductMatchRepository extends JpaRepository<ProductMatch, Long> {

    List<ProductMatch> findByStatusOrderByCreatedAtAsc(MatchStatus status);

    Optional<ProductMatch> findFirstByOffer_IdAndProduct_Id(Long offerId, Long productId);

    List<ProductMatch> findByOffer_Id(Long offerId);
}
