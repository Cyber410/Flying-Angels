package com.flyingangels.backend.event.dto;

import com.flyingangels.backend.event.EventParticipation;

public record EventParticipationResponse(Long participationId, Long athleteId, String firstName, String lastName,
        Long eventId, String eventName, Long meetId, String meetName, Long categoryId, String categoryName, String note) {
    public static EventParticipationResponse from(EventParticipation participation) {
        var event = participation.getEvent(); var athlete = participation.getAthlete();
        return new EventParticipationResponse(participation.getId(), athlete.getId(), athlete.getFirstName(), athlete.getLastName(),
                event.getId(), event.getName(), event.getMeet().getId(), event.getMeet().getName(),
                event.getCategory().getId(), event.getCategory().getName(), participation.getNote() == null ? "" : participation.getNote());
    }
}
