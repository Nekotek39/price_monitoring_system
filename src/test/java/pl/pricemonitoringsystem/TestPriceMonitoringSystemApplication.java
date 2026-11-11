package pl.pricemonitoringsystem;

import org.springframework.boot.SpringApplication;

public class TestPriceMonitoringSystemApplication {

	public static void main(String[] args) {
		SpringApplication.from(PriceMonitoringSystemApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
