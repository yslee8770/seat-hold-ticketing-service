package com.example.ticket.api.admin.dto;

import com.example.ticket.domain.event.EventStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.Instant;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AdminEventDto {

    public record AdminEventCreateRequest(
            @NotBlank String title,
            @NotNull Instant salesOpenAt,
            @NotNull Instant salesCloseAt
    ) {}

    public record AdminEventResponse(
            long eventId,
            String title,
            EventStatus status,          // DRAFT | OPEN | CLOSED
            Instant salesOpenAt,
            Instant salesCloseAt
    ) {}
}
