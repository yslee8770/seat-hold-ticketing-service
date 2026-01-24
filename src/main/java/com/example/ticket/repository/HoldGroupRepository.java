package com.example.ticket.repository;

import com.example.ticket.domain.hold.HoldGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;


public interface HoldGroupRepository extends JpaRepository<HoldGroup, Long> {
    void deleteAllByExpiresAtLessThanEqual(Instant now);
}
