package pl.pricemonitor.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pl.pricemonitor.persistence.entity.Offer;

import java.util.Collection;
import java.util.List;

public interface OfferRepository extends JpaRepository<Offer, Long> {

    List<Offer> findByShop_Id(Long shopId);

    List<Offer> findByProduct_Id(Long productId);

    List<Offer> findByProduct_IdAndShop_IdIn(Long productId, Collection<Long> shopIds);

    /** Offers that have never been through matching (no product and no match proposal). */
    @Query("select o from Offer o where o.product is null "
            + "and not exists (select m.id from ProductMatch m where m.offer = o)")
    List<Offer> findNeverMatched();

    long countByProductIsNotNull();
}
