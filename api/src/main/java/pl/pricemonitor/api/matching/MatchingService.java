package pl.pricemonitor.api.matching;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.pricemonitor.api.web.NotFoundException;
import pl.pricemonitor.core.matching.MatchMethod;
import pl.pricemonitor.core.matching.MatchResult;
import pl.pricemonitor.core.matching.MatchStatus;
import pl.pricemonitor.core.matching.OfferCandidate;
import pl.pricemonitor.core.matching.OfferMatcher;
import pl.pricemonitor.core.matching.ProductCandidate;
import pl.pricemonitor.persistence.entity.AppUser;
import pl.pricemonitor.persistence.entity.Offer;
import pl.pricemonitor.persistence.entity.Product;
import pl.pricemonitor.persistence.entity.ProductMatch;
import pl.pricemonitor.persistence.repository.AppUserRepository;
import pl.pricemonitor.persistence.repository.OfferRepository;
import pl.pricemonitor.persistence.repository.ProductMatchRepository;
import pl.pricemonitor.persistence.repository.ProductRepository;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Matches new offers to catalog products and handles manual verification. */
@Service
public class MatchingService {

    private static final Logger log = LoggerFactory.getLogger(MatchingService.class);

    private final OfferRepository offers;
    private final ProductRepository products;
    private final ProductMatchRepository matches;
    private final AppUserRepository users;
    private final OfferMatcher matcher;

    public MatchingService(OfferRepository offers, ProductRepository products, ProductMatchRepository matches,
                           AppUserRepository users, OfferMatcher matcher) {
        this.offers = offers;
        this.products = products;
        this.matches = matches;
        this.users = users;
        this.matcher = matcher;
    }

    @Transactional
    public MatchingSummary matchNewOffers() {
        List<Offer> pending = offers.findNeverMatched();
        if (pending.isEmpty()) {
            return new MatchingSummary(0, 0, 0, 0);
        }
        List<Product> catalog = products.findAll();
        Map<Long, Product> byId = new HashMap<>();
        catalog.forEach(p -> byId.put(p.getId(), p));
        List<ProductCandidate> candidates = catalog.stream()
                .map(p -> new ProductCandidate(p.getId(), p.getName(), p.getBrand(), p.getModel(), p.getGtin(), p.getMpn(),
                        p.getVariantAttributes()))
                .toList();
        int automatic = 0;
        int review = 0;
        int unmatched = 0;
        for (Offer offer : pending) {
            Map<String, String> attributes = new HashMap<>(offer.getVariantAttributes());
            attributes.remove("brand");
            OfferCandidate candidate = new OfferCandidate(offer.getName(), offer.getBrand(), offer.getGtin(),
                    offer.getMpn(), attributes, offer.getCondition());
            Optional<MatchResult> result = matcher.match(candidate, candidates);
            if (result.isEmpty()) {
                unmatched++;
                continue;
            }
            MatchResult r = result.get();
            Product product = byId.get(r.productId());
            matches.save(new ProductMatch(offer, product, r.method(), r.confidence(), r.status()));
            if (r.status() == MatchStatus.AUTOMATIC) {
                offer.setProduct(product);
                automatic++;
            } else {
                review++;
            }
        }
        log.info("Matching: {} offers, {} automatic, {} to review, {} unmatched", pending.size(), automatic, review,
                unmatched);
        return new MatchingSummary(pending.size(), automatic, review, unmatched);
    }

    @Transactional(readOnly = true)
    public List<MatchView> byStatus(MatchStatus status) {
        return matches.findByStatusOrderByCreatedAtAsc(status).stream().map(MatchingService::view).toList();
    }

    @Transactional
    public MatchView approve(Long matchId, Long reviewerId) {
        ProductMatch match = match(matchId);
        match.verify(MatchStatus.APPROVED, reviewer(reviewerId));
        match.getOffer().setProduct(match.getProduct());
        return view(match);
    }

    @Transactional
    public MatchView reject(Long matchId, Long reviewerId) {
        ProductMatch match = match(matchId);
        match.verify(MatchStatus.REJECTED, reviewer(reviewerId));
        Offer offer = match.getOffer();
        if (offer.getProduct() != null && offer.getProduct().getId().equals(match.getProduct().getId())) {
            offer.setProduct(null);
        }
        return view(match);
    }

    /** Manual match created by a person (e.g. for an offer the matcher could not match). */
    @Transactional
    public MatchView manual(ManualMatchRequest request) {
        Offer offer = offers.findById(request.offerId())
                .orElseThrow(() -> new NotFoundException("Oferta " + request.offerId() + " nie istnieje"));
        Product product = products.findById(request.productId())
                .orElseThrow(() -> new NotFoundException("Produkt " + request.productId() + " nie istnieje"));
        ProductMatch match = matches.findFirstByOffer_IdAndProduct_Id(offer.getId(), product.getId())
                .orElseGet(() -> matches.save(new ProductMatch(offer, product, MatchMethod.MANUAL, BigDecimal.ONE,
                        MatchStatus.APPROVED)));
        match.verify(MatchStatus.APPROVED, reviewer(request.reviewerId()));
        offer.setProduct(product);
        return view(match);
    }

    private ProductMatch match(Long id) {
        return matches.findById(id).orElseThrow(() -> new NotFoundException("Dopasowanie " + id + " nie istnieje"));
    }

    private AppUser reviewer(Long id) {
        return id == null ? null : users.findById(id).orElse(null);
    }

    private static MatchView view(ProductMatch m) {
        return new MatchView(m.getId(), m.getOffer().getId(), m.getOffer().getName(), m.getOffer().getShop().getName(),
                m.getProduct().getId(), m.getProduct().getName(), m.getMethod().name(), m.getConfidence(),
                m.getStatus().name(), m.getCreatedAt());
    }
}
