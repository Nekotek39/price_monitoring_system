package pl.pricemonitor.api.pricing;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pl.pricemonitor.core.pricing.PricingDecision;

import java.util.List;

/**
 * Seller's configuration of the dynamic pricing module. The seller id is in the path until authentication
 * is added (then it will come from the logged-in user).
 */
@RestController
@RequestMapping("/api/sellers/{sellerId}")
public class PricingRuleController {

    private final PricingRuleService service;

    public PricingRuleController(PricingRuleService service) {
        this.service = service;
    }

    @GetMapping("/rules")
    public List<PricingRuleView> list(@PathVariable Long sellerId) {
        return service.list(sellerId);
    }

    @GetMapping("/rules/{ruleId}")
    public PricingRuleView get(@PathVariable Long sellerId, @PathVariable Long ruleId) {
        return service.get(sellerId, ruleId);
    }

    @PostMapping("/rules")
    @ResponseStatus(HttpStatus.CREATED)
    public PricingRuleView create(@PathVariable Long sellerId, @Valid @RequestBody PricingRuleRequest request) {
        return service.create(sellerId, request);
    }

    @PutMapping("/rules/{ruleId}")
    public PricingRuleView update(@PathVariable Long sellerId, @PathVariable Long ruleId,
                                  @Valid @RequestBody PricingRuleRequest request) {
        return service.update(sellerId, ruleId, request);
    }

    @DeleteMapping("/rules/{ruleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long sellerId, @PathVariable Long ruleId) {
        service.delete(sellerId, ruleId);
    }

    /** Runs the rule immediately (useful for demonstration and tests). */
    @PostMapping("/rules/{ruleId}/evaluate")
    public PricingDecision evaluate(@PathVariable Long sellerId, @PathVariable Long ruleId) {
        return service.evaluateNow(sellerId, ruleId);
    }

    @GetMapping("/decisions")
    public List<DecisionView> decisions(@PathVariable Long sellerId) {
        return service.decisions(sellerId);
    }
}
