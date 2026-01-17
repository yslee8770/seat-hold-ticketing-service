package com.example.ticket.api.ticket.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class HoldDto {


    public record HoldCreateRequest(
            @NotEmpty
            @Size(max = 4)
            List<Long> seatIds
    ) {}

    public record HoldCreateResponse(
            long eventId,
            String holdToken,
            int seatCount
    ) {}
}
