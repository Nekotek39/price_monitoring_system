package pl.pricemonitor.fetcher.update;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.pricemonitor.core.feed.FeedOffer;
import pl.pricemonitor.core.matching.ProductCondition;
import pl.pricemonitor.fetcher.queue.ClaimedJob;
import pl.pricemonitor.persistence.entity.JobType;
import pl.pricemonitor.persistence.entity.Offer;
import pl.pricemonitor.persistence.entity.PriceRecord;
import pl.pricemonitor.persistence.entity.PriceSource;
import pl.pricemonitor.persistence.entity.Shop;
import pl.pricemonitor.persistence.repository.OfferRepository;
import pl.pricemonitor.persistence.repository.PriceRecordRepository;
import pl.pricemonitor.persistence.repository.ShopRepository;
import pl.pricemonitor.persistence.service.PopularityService;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Writes one downloaded price file into the database. The scope depends on the job type:
 * IMPORT - all offers (new ones are created), CYCLIC - known offers of popular products,
 * ON_DEMAND - offers of the requested product.
 */
@Service
public class OfferUpdater {

    private final ShopRepository shops;
    private final OfferRepository offers;
    private final PriceRecordRepository prices;
    private final PopularityService popularity;

    public OfferUpdater(ShopRepository shops, OfferRepository offers, PriceRecordRepository prices,
                        PopularityService popularity) {
        this.shops = shops;
        this.offers = offers;
        this.prices = prices;
        this.popularity = popularity;
    }

    @Transactional
    public UpdateResult apply(ClaimedJob job, List<FeedOffer> feed, Instant now) {
        Shop shop = shops.getReferenceById(job.shopId());
        Map<String, Offer> existing = offers.findByShop_Id(job.shopId()).stream()
                .collect(Collectors.toMap(Offer::getShopOfferId, Function.identity(), (a, b) -> a));
        Predicate<Offer> inScope = scope(job);
        PriceSource source = PriceSource.valueOf(job.type().name());

        int updated = 0;
        int created = 0;
        int priceChanges = 0;
        Set<String> seen = new HashSet<>();
        for (FeedOffer item : feed) {
            Offer offer = existing.get(item.shopOfferId());
            if (offer == null) {
                if (job.type() != JobType.IMPORT) {
                    continue;
                }
                offer = new Offer(shop, item.shopOfferId());
                created++;
            } else if (!inScope.test(offer)) {
                continue;
            }
            seen.add(item.shopOfferId());
            copyFields(item, offer, now);
            offer = offers.save(offer);
            PriceRecord latest = prices.findFirstByOffer_IdOrderByFetchedAtDesc(offer.getId()).orElse(null);
            if (latest == null || latest.differsFrom(item.price(), item.shipping(), item.available())) {
                prices.save(new PriceRecord(offer, item.price(), item.shipping(), item.available(), source, now));
                priceChanges++;
            }
            updated++;
        }

        // Offers in scope that disappeared from the file are treated as unavailable.
        for (Offer offer : existing.values()) {
            if (seen.contains(offer.getShopOfferId()) || !inScope.test(offer)) {
                continue;
            }
            offer.setLastFetchedAt(now);
            PriceRecord latest = prices.findFirstByOffer_IdOrderByFetchedAtDesc(offer.getId()).orElse(null);
            if (latest != null && latest.isAvailable()) {
                prices.save(new PriceRecord(offer, latest.getPrice(), latest.getShippingCost(), false, source, now));
                priceChanges++;
            }
        }
        return new UpdateResult(updated, created, priceChanges);
    }

    private Predicate<Offer> scope(ClaimedJob job) {
        return switch (job.type()) {
            case IMPORT -> offer -> true;
            case CYCLIC -> {
                Set<Long> popular = popularity.popularProductIds();
                yield offer -> offer.getProduct() != null && popular.contains(offer.getProduct().getId());
            }
            case ON_DEMAND -> offer -> offer.getProduct() != null
                    && Objects.equals(offer.getProduct().getId(), job.productId());
        };
    }

    private static void copyFields(FeedOffer item, Offer offer, Instant now) {
        offer.setName(item.name());
        offer.setUrl(item.url());
        offer.setGtin(item.gtin());
        offer.setMpn(item.mpn());
        Map<String, String> attributes = new HashMap<>(item.attributes());
        if (item.brand() != null) {
            attributes.put("brand", item.brand());
        }
        offer.setVariantAttributes(attributes);
        offer.setCondition(ProductCondition.resolve(item.condition(), item.name()));
        offer.setLastFetchedAt(now);
    }
}
