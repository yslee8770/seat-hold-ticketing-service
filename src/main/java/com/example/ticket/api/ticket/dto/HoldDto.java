package com.example.ticket.api.ticket.dto;

import com.example.ticket.domain.idempotency.HoldIdempotency;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class HoldDto {


    public record HoldCreateRequest(
            @NotEmpty
            @Size(max = 4)
            List<Long> seatIds
    ) {
    }

    public record HoldCreateResponse(
            long eventId,
            UUID holdToken,
            int seatCount
    ) {
        public static HoldCreateResponse from(long eventId, UUID holdToken, int seatCount) {
            return new HoldCreateResponse(eventId, holdToken, seatCount);
        }

        public static HoldCreateResponse from(HoldIdempotency savedHoldIdempotency) {
            return  new HoldCreateResponse(
                    savedHoldIdempotency.getEventId(),
                    savedHoldIdempotency.getHoldGroupId(),
                    savedHoldIdempotency.getSeatCount()
            );
        }
    }
}
