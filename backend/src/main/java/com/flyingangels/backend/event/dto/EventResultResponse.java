package com.flyingangels.backend.event.dto;
import com.flyingangels.backend.event.*;
import com.flyingangels.backend.athlete.*;
import java.math.BigDecimal;
import java.time.Instant;

public record EventResultResponse(Long resultId, Long athleteId, String firstName, String lastName,
        Long categoryId, String categoryName, String result, BigDecimal score,
        Long eventId, String eventName, Long meetId, String meetName, ResultUnit unit, ResultStatus status,
        Instant recordedAt, String source) {
    public static EventResultResponse from(EventResult r) {
        var e=r.getEvent();
        return new EventResultResponse(r.getId(),r.getAthlete().getId(),r.getAthlete().getFirstName(),r.getAthlete().getLastName(),
                r.getCategory().getId(),r.getCategory().getName(),r.getResult(),r.getScore(),
                e==null?null:e.getId(),e==null?null:e.getName(),e==null?null:e.getMeet().getId(),e==null?null:e.getMeet().getName(),
                r.getUnit(),r.getStatus(),r.getRecordedAt(),r.getSource());
    }
    public static EventResultResponse fromLegacy(Athlete a, AthleteEvent e) {
        return new EventResultResponse(null,a.getId(),a.getFirstName(),a.getLastName(),null,e.getEventName(),e.getResult(),null,
                null,null,null,null,null,ResultStatus.UNPARSED,null,"LEGACY_IMPORT");
    }
}
