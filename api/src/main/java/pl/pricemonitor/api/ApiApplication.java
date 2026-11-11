package pl.pricemonitor.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Import;
import pl.pricemonitor.persistence.PersistenceConfig;

/**
 * Main application: REST API with live updates (SSE), indexing scheduler, offer matching and
 * the dynamic pricing module. Owns the database schema (Flyway migrations from the persistence module).
 */
@SpringBootApplication
@Import(PersistenceConfig.class)
@ConfigurationPropertiesScan
public class ApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiApplication.class, args);
    }
}
