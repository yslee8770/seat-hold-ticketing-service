package com.example.ticket.repository;

import com.example.ticket.domain.hold.HoldGroupSeat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

public interface HoldGroupSeatRepository extends JpaRepository<HoldGroupSeat, Long> {


    int deleteAllByExpiresAtLessThanEqual(Instant now);
}
