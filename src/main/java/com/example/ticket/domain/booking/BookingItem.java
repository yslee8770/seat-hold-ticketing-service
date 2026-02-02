package com.example.ticket.domain.booking;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

@Entity
@Table(
        name = "booking_items",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_booking_items_seat", columnNames = {"seat_id"})
        },
        indexes = {
                @Index(name = "ix_booking_items_booking", columnList = "booking_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BookingItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "booking_item_id")
    private Long id;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(name = "seat_id", nullable = false)
    private Long seatId;

    @Column(name = "price", nullable = false)
    private long price;

    private BookingItem(Long bookingId, Long seatId, long price) {
        this.bookingId = Objects.requireNonNull(bookingId);
        this.seatId = Objects.requireNonNull(seatId);
        this.price = price;
    }

    public static BookingItem create(Long bookingId, Long seatId, long price) {
        return new BookingItem(bookingId, seatId, price);
    }
}

