package com.example.ticket.domain.hold;


import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class HoldTimes {

    public static final long HOLD_TTL_SECONDS = 90;

    public static Instant now(Clock clock) {
        return Instant.now(Objects.requireNonNull(clock));
    }

    public static Instant holdUntil(Clock clock) {
        return now(clock).plusSeconds(HOLD_TTL_SECONDS);
    }

    public static Instant holdUntilFrom(Instant now) {
        return Objects.requireNonNull(now).plusSeconds(HOLD_TTL_SECONDS);
    }
}
