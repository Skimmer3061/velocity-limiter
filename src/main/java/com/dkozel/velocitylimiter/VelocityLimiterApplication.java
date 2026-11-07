package com.dkozel.velocitylimiter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class VelocityLimiterApplication {

	public static void main(String[] args) {
		SpringApplication.run(VelocityLimiterApplication.class, args);
	}

}
