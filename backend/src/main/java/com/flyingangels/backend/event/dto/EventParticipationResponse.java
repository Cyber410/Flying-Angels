package com.flyingangels.backend.event.dto;
import com.flyingangels.backend.event.EventParticipation;
public record EventParticipationResponse(Long participationId, Long athleteId, String firstName, String lastName,
        Long eventId, String eventName, Long meetId, String meetName, Long categoryId, String categoryName) {
    public static EventParticipationResponse from(EventParticipation p) {
        var e=p.getEvent(); var a=p.getAthlete();
        return new EventParticipationResponse(p.getId(),a.getId(),a.getFirstName(),a.getLastName(),e.getId(),e.getName(),
                e.getMeet().getId(),e.getMeet().getName(),e.getCategory().getId(),e.getCategory().getName());
    }
}
