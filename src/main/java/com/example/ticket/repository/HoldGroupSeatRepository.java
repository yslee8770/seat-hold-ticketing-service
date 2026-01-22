package com.example.ticket.repository;

import com.example.ticket.domain.hold.HoldGroupSeat;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HoldGroupSeatRepository extends JpaRepository<HoldGroupSeat, Long> {
}
