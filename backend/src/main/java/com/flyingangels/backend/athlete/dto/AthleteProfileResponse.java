package com.flyingangels.backend.athlete.dto;
import com.flyingangels.backend.athlete.*;
import com.flyingangels.backend.event.*;
import com.flyingangels.backend.event.dto.*;
import java.time.LocalDate;
import java.util.*;

public record AthleteProfileResponse(Long id,String firstName,String lastName,LocalDate dateOfBirth,String gender,
        String note,String flyStatus,String flyaStatus,List<EventResultResponse> events,List<EventParticipationResponse> participations) {
    public static AthleteProfileResponse from(Athlete athlete,List<EventResult> results) { return from(athlete,results,List.of()); }
    public static AthleteProfileResponse from(Athlete a,List<EventResult> results,List<EventParticipation> participations) {
        List<EventResultResponse> combined=new ArrayList<>(results.stream().map(EventResultResponse::from).toList());
        // Only suppress a legacy entry when a historical central entry represents that same value.
        for (var legacy:a.getEvents()) {
            boolean represented=results.stream().anyMatch(r->r.getCategory().getName().equalsIgnoreCase(legacy.getEventName())
                && Objects.equals(r.getResult(),legacy.getResult()));
            if (!represented) combined.add(EventResultResponse.fromLegacy(a,legacy));
        }
        return new AthleteProfileResponse(a.getId(),a.getFirstName(),a.getLastName(),a.getDateOfBirth(),a.getGender(),a.getNote(),
            Athlete.normalizeStatus(a.getFlyStatus()),Athlete.normalizeStatus(a.getFlyaStatus()),List.copyOf(combined),
            participations.stream().map(EventParticipationResponse::from).toList());
    }
}
