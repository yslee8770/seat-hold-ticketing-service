package com.example.ticket.api.ticket.dto;

import com.example.ticket.domain.event.SeatStatus;
import com.example.ticket.domain.seat.Seat;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SeatQueryDto {

    public record SeatListResponse(
            long eventId,
            List<SeatDto> seats
    ) {
        public static SeatListResponse from(long eventId, List<Seat> seats) {
            List<SeatDto> seatDtos = seats.stream()
                    .map(SeatDto::from)
                    .toList();
            return new SeatListResponse(eventId, seatDtos);
        }
    }

    public record SeatDto(
            long seatId,
            String zoneCode,
            String seatNo,
            long price,
            SeatStatus status
    ) {
        public static SeatDto from(Seat s) {
            return new SeatDto(
                    s.getId(),
                    s.getZoneCode(),
                    s.getSeatNo(),
                    s.getPrice(),
                    s.getStatus()
            );
        }
    }
}
