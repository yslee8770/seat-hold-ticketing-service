package com.example.ticket.api.ticket.dto;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.Instant;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AdminExpireSweepDto {

    public record AdminExpireSweepResponse(
            int expiredReleasedCount,
            Instant sweptAt
    ) {
        public static AdminExpireSweepResponse from(
                int expiredReleasedCount,
                Instant sweptAt
        ) {
            return new AdminExpireSweepResponse(expiredReleasedCount, sweptAt);
        }
    }
}
