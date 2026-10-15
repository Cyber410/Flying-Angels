package com.flyingangels.backend.note;
import com.flyingangels.backend.athlete.Athlete;
import com.flyingangels.backend.event.Event;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
@Entity @Table(name="athlete_notes") @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class AthleteNote {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional=false) @JoinColumn(name="athlete_id",nullable=false) private Athlete athlete;
    @ManyToOne @JoinColumn(name="event_id") private Event event;
    @Column(nullable=false,length=2000) private String text;
    @Column(nullable=false) private Instant createdAt;
    public AthleteNote(Athlete athlete,Event event,String text) { this.athlete=athlete;this.event=event;this.text=text.trim();this.createdAt=Instant.now(); }
}
