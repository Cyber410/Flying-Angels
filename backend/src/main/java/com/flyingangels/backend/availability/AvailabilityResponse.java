package com.flyingangels.backend.availability;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

/** Each confirmation is appended; edits never overwrite earlier responses. */
@Entity @Table(name = "availability_responses", indexes = @Index(name = "idx_availability_response_request", columnList = "request_id,id"))
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AvailabilityResponse {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "request_id", nullable = false) private AvailabilityRequest request;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private AvailabilityDtos.Answer answer;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private AvailabilityDtos.Channel communicationMethod;
    @Column(nullable = false) private long assignmentRevision;
    @Column(nullable = false) private Instant respondedAt;
    private Long staffUserId;
    private Long athleteUserId;
    private Long submittedVersion;
    @Column(length = 1000) private String note;

    public AvailabilityResponse(AvailabilityRequest request, AvailabilityDtos.Answer answer, AvailabilityDtos.Channel channel,
                                Instant at, Long staffUserId, Long athleteUserId, String note) {
        this.request = request; this.answer = answer; this.communicationMethod = channel;
        this.assignmentRevision = request.getAssignmentRevision(); this.respondedAt = at;
        this.staffUserId = staffUserId; this.athleteUserId = athleteUserId;
        this.note = note == null || note.isBlank() ? null : note.trim();
        this.submittedVersion = request.getVersion();
    }
    public boolean isRetry(long version, AvailabilityDtos.Answer answer, AvailabilityDtos.Channel channel,
                            Long staffUserId, Long athleteUserId, String note) {
        String normalizedNote = note == null || note.isBlank() ? null : note.trim();
        return submittedVersion != null && submittedVersion == version && this.answer == answer && communicationMethod == channel
                && java.util.Objects.equals(this.staffUserId, staffUserId) && java.util.Objects.equals(this.athleteUserId, athleteUserId)
                && java.util.Objects.equals(this.note, normalizedNote);
    }
}
