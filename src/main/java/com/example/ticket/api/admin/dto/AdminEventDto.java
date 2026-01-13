package com.example.ticket.api.admin.dto;

import com.example.ticket.domain.event.Event;
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
    ) {
        public Event toEvent() {
            return Event.draft(
                    title,
                    salesOpenAt,
                    salesCloseAt
            );
        }
    }

    public record AdminEventResponse(
            long eventId,
            String title,
            EventStatus status,          // DRAFT | OPEN | CLOSED
            Instant salesOpenAt,
            Instant salesCloseAt
    ) {
        public static AdminEventResponse from(Event event) {
            return new AdminEventResponse(
                    event.getId(),
                    event.getTitle(),
                    event.getStatus(),
                    event.getSalesOpenAt(),
                    event.getSalesCloseAt()
            );
        }
    }
}
