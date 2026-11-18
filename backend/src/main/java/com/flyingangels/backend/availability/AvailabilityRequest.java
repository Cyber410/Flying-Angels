package com.flyingangels.backend.availability;

import com.flyingangels.backend.athlete.Athlete;
import com.flyingangels.backend.meet.Meet;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "availability_requests", uniqueConstraints = @UniqueConstraint(name = "uk_availability_meet_athlete", columnNames = {"meet_id", "athlete_id"}))
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AvailabilityRequest {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "meet_id", nullable = false) private Meet meet;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "athlete_id", nullable = false) private Athlete athlete;
    @Column(nullable = false) private long assignmentRevision;
    @Column(nullable = false) private int assignmentCount;
    @Version private long version;
    @Enumerated(EnumType.STRING) private AvailabilityDtos.Answer lastAnswer;
    private Long answeredRevision;
    private Instant respondedAt;

    public AvailabilityRequest(Meet meet, Athlete athlete, int count) {
        this.meet = meet; this.athlete = athlete; this.assignmentCount = count; this.assignmentRevision = 1;
    }
    public void assignmentAdded() { assignmentCount++; assignmentRevision++; }
    public void proposalChanged() { assignmentRevision++; }
    public void assignmentRemoved() {
        if (assignmentCount < 1) throw new IllegalStateException("Availability assignment count is inconsistent");
        assignmentCount--; assignmentRevision++;
    }
    public boolean isActive() { return assignmentCount > 0; }
    public AvailabilityDtos.Status status() {
        return lastAnswer == null || answeredRevision == null || answeredRevision != assignmentRevision
                ? AvailabilityDtos.Status.PENDING : AvailabilityDtos.Status.valueOf(lastAnswer.name());
    }
    public void respond(AvailabilityDtos.Answer answer, Instant at) {
        lastAnswer = answer; answeredRevision = assignmentRevision; respondedAt = at;
    }
}
