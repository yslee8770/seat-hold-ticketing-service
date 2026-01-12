package com.example.ticket.api.admin.dto;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AdminSeatSummaryDto {

    public record AdminSeatSummaryResponse(
            long eventId,
            long availableCount,
            long heldCount,
            long soldCount
    ) {}
}
