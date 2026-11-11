package pl.pricemonitor.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.pricemonitor.persistence.entity.AppUser;
import pl.pricemonitor.persistence.entity.UserRole;

import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByEmail(String email);

    List<AppUser> findByRole(UserRole role);
}
