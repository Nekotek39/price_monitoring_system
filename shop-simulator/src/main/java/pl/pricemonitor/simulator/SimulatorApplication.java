package pl.pricemonitor.simulator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Five simulated shops publishing price files (XML or JSON), a price change scenario with a log of true
 * prices (ground truth for the experiments) and a mock seller shop API used by the dynamic pricing module.
 */
@SpringBootApplication
public class SimulatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(SimulatorApplication.class, args);
    }
}
