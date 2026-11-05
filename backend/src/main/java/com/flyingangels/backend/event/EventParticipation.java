package com.flyingangels.backend.event;

import com.flyingangels.backend.athlete.Athlete;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "event_participations", uniqueConstraints = @UniqueConstraint(name = "uk_participation", columnNames = {"event_id", "athlete_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventParticipation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false) @JoinColumn(name = "event_id", nullable = false) private Event event;
    @ManyToOne(optional = false) @JoinColumn(name = "athlete_id", nullable = false) private Athlete athlete;
    @Column(length = 2000) private String note;
    @Version @Column(nullable = false, columnDefinition = "bigint default 0") private long version;
    public EventParticipation(Event event, Athlete athlete) { this.event = event; this.athlete = athlete; }
    public void setNote(String note) { this.note = note == null || note.isBlank() ? null : note.trim(); }
}
