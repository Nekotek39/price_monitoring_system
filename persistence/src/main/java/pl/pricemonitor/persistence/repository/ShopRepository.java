package pl.pricemonitor.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.pricemonitor.persistence.entity.Shop;
import pl.pricemonitor.persistence.entity.ShopType;

import java.util.List;

public interface ShopRepository extends JpaRepository<Shop, Long> {

    List<Shop> findByType(ShopType type);

    List<Shop> findByOwner_IdAndType(Long ownerId, ShopType type);
}
