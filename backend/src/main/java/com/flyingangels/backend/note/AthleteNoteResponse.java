package com.flyingangels.backend.note;
import java.time.Instant;
public record AthleteNoteResponse(Long id,Long athleteId,String text,Instant createdAt,Long eventId,String eventName,Long meetId,String meetName) {
    public static AthleteNoteResponse from(AthleteNote n) {
        var e=n.getEvent(); return new AthleteNoteResponse(n.getId(),n.getAthlete().getId(),n.getText(),n.getCreatedAt(),
            e==null?null:e.getId(),e==null?null:e.getName(),e==null?null:e.getMeet().getId(),e==null?null:e.getMeet().getName());
    }
}
