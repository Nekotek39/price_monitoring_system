package pl.pricemonitor.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.pricemonitor.persistence.entity.DecisionStatus;
import pl.pricemonitor.persistence.entity.PricingDecisionRecord;

import java.util.List;
import java.util.Optional;

public interface PricingDecisionRepository extends JpaRepository<PricingDecisionRecord, Long> {

    Optional<PricingDecisionRecord> findFirstByRule_IdAndStatusOrderByCreatedAtDesc(Long ruleId, DecisionStatus status);

    Optional<PricingDecisionRecord> findFirstByRule_IdOrderByCreatedAtDesc(Long ruleId);

    List<PricingDecisionRecord> findTop100ByRule_Owner_IdOrderByCreatedAtDesc(Long ownerId);
}
