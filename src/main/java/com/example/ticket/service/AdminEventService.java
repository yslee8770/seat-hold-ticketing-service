package com.example.ticket.service;

import com.example.ticket.api.admin.dto.AdminEventDto.AdminEventCreateRequest;
import com.example.ticket.api.admin.dto.AdminEventDto.AdminEventResponse;
import com.example.ticket.common.BusinessRuleViolationException;
import com.example.ticket.common.ErrorCode;
import com.example.ticket.domain.event.Event;
import com.example.ticket.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminEventService {

    private final EventRepository eventRepository;

    @Transactional
    public AdminEventResponse createDraft(AdminEventCreateRequest request) {
        if (!request.salesOpenAt().isBefore(request.salesCloseAt())) {
            throw new BusinessRuleViolationException(ErrorCode.INVALID_EVENT_SALES_WINDOW);
        }
        Event event = request.toEvent();
        return AdminEventResponse.from(eventRepository.save(event));
    }

    @Transactional
    public AdminEventResponse open(long eventId) {
        Event event =  eventRepository.findById(eventId)
                .orElseThrow(() -> new BusinessRuleViolationException(ErrorCode.EVENT_NOT_FOUND));
        event.open();
        return AdminEventResponse.from(event);
    }

    @Transactional
    public AdminEventResponse close(long eventId) {
        Event event =  eventRepository.findById(eventId)
                .orElseThrow(() -> new BusinessRuleViolationException(ErrorCode.EVENT_NOT_FOUND));
        event.close();
        return AdminEventResponse.from(event);
    }
}
