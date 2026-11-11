package pl.pricemonitor.api.matching;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.pricemonitor.core.matching.MatchStatus;

import java.util.List;

/** Manual verification of offer matches (pairs close to the threshold). */
@RestController
@RequestMapping("/api/matches")
public class MatchController {

    private final MatchingService service;

    public MatchController(MatchingService service) {
        this.service = service;
    }

    @GetMapping
    public List<MatchView> list(@RequestParam(defaultValue = "TO_REVIEW") MatchStatus status) {
        return service.byStatus(status);
    }

    @PostMapping("/{id}/approve")
    public MatchView approve(@PathVariable Long id, @RequestParam(required = false) Long reviewerId) {
        return service.approve(id, reviewerId);
    }

    @PostMapping("/{id}/reject")
    public MatchView reject(@PathVariable Long id, @RequestParam(required = false) Long reviewerId) {
        return service.reject(id, reviewerId);
    }

    @PostMapping
    public MatchView manual(@Valid @RequestBody ManualMatchRequest request) {
        return service.manual(request);
    }

    @PostMapping("/run")
    public MatchingSummary run() {
        return service.matchNewOffers();
    }
}
