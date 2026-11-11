package pl.pricemonitor.fetcher.worker;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.pricemonitor.persistence.entity.ShopStatus;
import pl.pricemonitor.persistence.repository.ShopRepository;

import java.time.Instant;

/** Marks shops as (un)available, so that jobs for an unavailable shop wait instead of failing repeatedly. */
@Service
public class ShopAvailability {

    private final ShopRepository shops;

    public ShopAvailability(ShopRepository shops) {
        this.shops = shops;
    }

    @Transactional
    public void markUnavailable(long shopId, Instant until) {
        shops.findById(shopId).ifPresent(shop -> shop.markUnavailable(until));
    }

    @Transactional
    public void markAvailable(long shopId) {
        shops.findById(shopId).filter(shop -> shop.getStatus() == ShopStatus.UNAVAILABLE)
                .ifPresent(shop -> shop.markAvailable());
    }
}
