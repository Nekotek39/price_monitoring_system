package pl.pricemonitor.persistence;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.time.Clock;

/**
 * Shared persistence configuration imported by the api and fetcher applications
 * (their main classes live in other packages, so entities and repositories must be registered explicitly).
 */
@Configuration
@EntityScan(basePackages = "pl.pricemonitor.persistence.entity")
@EnableJpaRepositories(basePackages = "pl.pricemonitor.persistence.repository")
@ComponentScan(basePackages = "pl.pricemonitor.persistence.service")
public class PersistenceConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
