package pl.pricemonitor.fetcher;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Import;
import pl.pricemonitor.persistence.PersistenceConfig;

/**
 * Data fetching process. Runs independently of the main application (it can be restarted separately or
 * started in several instances); both communicate only through the job queue table in PostgreSQL.
 */
@SpringBootApplication
@Import(PersistenceConfig.class)
@ConfigurationPropertiesScan
public class FetcherApplication {

    public static void main(String[] args) {
        SpringApplication.run(FetcherApplication.class, args);
    }
}
