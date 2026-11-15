package com.flyingangels.backend.availability;

import jakarta.validation.constraints.*;
import java.time.*;
import java.util.List;

public final class AvailabilityDtos {
    private AvailabilityDtos() {}
    public enum Status { PENDING, AWAITING_RESPONSE, AVAILABLE, UNAVAILABLE;
        public Status canonical() { return this == AWAITING_RESPONSE ? PENDING : this; }
    }
    public enum Answer { AVAILABLE, UNAVAILABLE }
    public enum Channel { PORTAL, PHONE, WHATSAPP, EMAIL, TEXT, IN_PERSON, OTHER }
    public record Summary(long total, long awaitingResponse, long available, long unavailable) {
        @com.fasterxml.jackson.annotation.JsonProperty("pending") public long pending() { return awaitingResponse; }
        public static Summary empty() { return new Summary(0, 0, 0, 0); }
    }
    public record OverviewFilters(Long meetId, Status status, com.flyingangels.backend.meet.MeetView view,
                                  String search, LocalDate asOf) {}
    public record OverviewPage(OverviewFilters filters, List<OverviewRow> content,
                               int page, int size, long totalElements, int totalPages, Summary summary) {
        public OverviewPage(OverviewFilters filters, List<OverviewRow> content, int page, int size, long totalElements, int totalPages) {
            this(filters, content, page, size, totalElements, totalPages, Summary.empty());
        }
    }
    public record OverviewRow(Long id, Long athleteId, String firstName, String lastName,
                              Long meetId, String meetName, LocalDate startDate, LocalDate endDate,
                              Status status, int assignedEventCount, boolean canRespond, long version,
                              long assignmentRevision, Answer lastAnswer, Long answeredRevision, Instant respondedAt,
                              List<AssignedEvent> events, ResponseAudit latestResponse, Instant responseDeadline) {
        public static OverviewRow from(AvailabilityRequest request, Clock clock, List<AssignedEvent> events, ResponseAudit latestResponse) {
            var athlete = request.getAthlete(); var meet = request.getMeet();
            return new OverviewRow(request.getId(), athlete.getId(), athlete.getFirstName(), athlete.getLastName(),
                    meet.getId(), meet.getName(), meet.getStartDate(), meet.getEndDate(), request.status(),
                    request.getAssignmentCount(), request.isActive() && com.flyingangels.backend.meet.MeetView.canRespond(meet, clock), request.getVersion(),
                    request.getAssignmentRevision(), request.getLastAnswer(), request.getAnsweredRevision(), request.getRespondedAt(), events, latestResponse,
                    com.flyingangels.backend.meet.MeetView.responseDeadline(meet, clock));
        }
    }
    public record PortalReply(@NotNull Answer status, @NotNull @PositiveOrZero Long version,
                              @NotNull @Positive Long assignmentRevision) {}
    public record StaffReply(@NotNull Answer status, @NotNull @PositiveOrZero Long version,
                             @NotNull @Positive Long assignmentRevision, @NotNull Channel communicationMethod,
                             @Size(max = 1000) String note) {}
    public record AssignedEvent(Long eventId, String eventName, Long categoryId, String categoryName, LocalDate eventDate) {}
    /** Safe for the athlete account: no staff notes or contact information. */
    public record RequestView(Long id, Long athleteId, String firstName, String lastName,
                              Long meetId, String meetName, LocalDate startDate, LocalDate endDate, String location,
                              boolean active, boolean canRespond, Status status, long version, long assignmentRevision,
                              Answer lastAnswer, Long answeredRevision, Instant respondedAt, List<AssignedEvent> events,
                              Instant responseDeadline, String timeZone) {}
    public record ResponseAudit(Long id, Answer status, Channel communicationMethod, long assignmentRevision,
                                Instant respondedAt, String recordedBy, Long staffUserId, String note, Long athleteUserId) {
        static ResponseAudit from(AvailabilityResponse response) {
            return new ResponseAudit(response.getId(), response.getAnswer(), response.getCommunicationMethod(),
                    response.getAssignmentRevision(), response.getRespondedAt(),
                    response.getCommunicationMethod() == Channel.PORTAL ? "ATHLETE_OR_PARENT" : "STAFF",
                    response.getStaffUserId(), response.getNote(), response.getAthleteUserId());
        }
    }
}
