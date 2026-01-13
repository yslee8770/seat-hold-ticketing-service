package com.example.ticket.repository.dto;

import com.example.ticket.domain.event.SeatStatus;

public record SeatStatusCount(SeatStatus status, long count) {}