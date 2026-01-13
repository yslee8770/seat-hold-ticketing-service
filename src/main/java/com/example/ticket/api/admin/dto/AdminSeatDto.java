package com.example.ticket.api.admin.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AdminSeatDto {

    public record AdminSeatBulkUpsertRequest(
            @NotEmpty @Valid List<AdminSeatCreateDto> seats
    ) {}

    public record AdminSeatCreateDto(
            @NotBlank String zoneCode,
            @NotBlank String seatNo,
            @PositiveOrZero long price
    ) {}

    public record AdminSeatBulkUpsertResponse(
            long eventId,
            int createdCount
    ) {
        public static AdminSeatBulkUpsertResponse of(
                long eventId,
                int createdCount
        ){
            return new AdminSeatBulkUpsertResponse(
                    eventId,
                    createdCount);
        }
    }
}
