package pl.pricemonitor.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.pricemonitor.persistence.entity.PricingRule;

import java.util.List;
import java.util.Optional;

public interface PricingRuleRepository extends JpaRepository<PricingRule, Long> {

    List<PricingRule> findByProduct_IdAndActiveTrue(Long productId);

    List<PricingRule> findByOwner_IdOrderByIdAsc(Long ownerId);

    Optional<PricingRule> findByIdAndOwner_Id(Long id, Long ownerId);

    boolean existsByOwner_IdAndProduct_Id(Long ownerId, Long productId);
}
