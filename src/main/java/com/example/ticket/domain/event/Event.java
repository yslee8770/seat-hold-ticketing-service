package com.example.ticket.domain.event;

import com.example.ticket.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(
        name = "events",
        indexes = {
                @Index(name = "ix_events_status", columnList = "status"),
                @Index(name = "ix_events_sales_window", columnList = "sales_open_at, sales_close_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Event extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_id")
    private Long id;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "sales_open_at", nullable = false)
    private Instant salesOpenAt;

    @Column(name = "sales_close_at", nullable = false)
    private Instant salesCloseAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private EventStatus status;

    private Event(String title, Instant salesOpenAt, Instant salesCloseAt, EventStatus status) {
        this.title = Objects.requireNonNull(title);
        this.salesOpenAt = Objects.requireNonNull(salesOpenAt);
        this.salesCloseAt = Objects.requireNonNull(salesCloseAt);
        this.status = Objects.requireNonNull(status);
    }

    public static Event draft(String title, Instant salesOpenAt, Instant salesCloseAt) {
        return new Event(title, salesOpenAt, salesCloseAt, EventStatus.DRAFT);
    }

    public void open() {
        this.status = EventStatus.OPEN;
    }

    public void close() {
        this.status = EventStatus.CLOSED;
    }
}
