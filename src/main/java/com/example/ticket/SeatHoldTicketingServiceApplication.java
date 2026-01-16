package com.example.ticket;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class SeatHoldTicketingServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(SeatHoldTicketingServiceApplication.class, args);
	}

}
