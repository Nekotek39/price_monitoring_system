package pl.pricemonitor.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.pricemonitor.persistence.entity.Setting;

public interface SettingRepository extends JpaRepository<Setting, String> {
}
