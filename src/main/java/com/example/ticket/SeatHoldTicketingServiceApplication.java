package com.example.ticket;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.resilience.annotation.EnableResilientMethods;

@SpringBootApplication
@EnableJpaAuditing
@EnableResilientMethods
public class SeatHoldTicketingServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(SeatHoldTicketingServiceApplication.class, args);
	}

}
