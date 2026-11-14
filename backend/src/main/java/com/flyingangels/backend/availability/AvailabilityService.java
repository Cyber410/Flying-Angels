package com.flyingangels.backend.availability;

import com.flyingangels.backend.athlete.*;
import com.flyingangels.backend.auth.StaffPrincipal;
import com.flyingangels.backend.event.*;
import com.flyingangels.backend.meet.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
import static com.flyingangels.backend.availability.AvailabilityDtos.*;

@Service @RequiredArgsConstructor
public class AvailabilityService {
    private final AvailabilityRequestRepository requests;
    private final AvailabilityResponseRepository responses;
    private final EventParticipationRepository assignments;
    private final MeetRepository meets;
    private final AthleteRepository athletes;
    private final Clock clock;

    /** The same meet lock protects first-request creation, assignment changes and replies. */
    @Transactional
    public void lockMeet(Long meetId) {
        meets.lockById(meetId).orElseThrow(() -> new MeetService.MeetNotFoundException(meetId));
    }

    @Transactional
    public void assignmentAdded(Event event, Athlete athlete) {
        lockMeet(event.getMeet().getId());
        var existing = requests.lockPair(event.getMeet().getId(), athlete.getId());
        if (existing.isPresent()) existing.get().assignmentAdded();
        else requests.save(new AvailabilityRequest(event.getMeet(), athlete,
                Math.toIntExact(assignments.countByEventMeetIdAndAthleteId(event.getMeet().getId(), athlete.getId()))));
    }

    @Transactional
    public void assignmentRemoved(Long meetId, Long athleteId) {
        lockMeet(meetId);
        requests.lockPair(meetId, athleteId).ifPresent(AvailabilityRequest::assignmentRemoved);
    }
    @Transactional
    public void proposalChangedForMeet(Long meetId) {
        lockMeet(meetId);
        requests.findByMeetIdAndAssignmentCountGreaterThanOrderByAthleteLastNameAscAthleteFirstNameAscIdAsc(meetId, 0)
                .forEach(r -> r.proposalChanged());
    }
    @Transactional
    public void proposalChangedForEvent(Event event) {
        lockMeet(event.getMeet().getId());
        event.getParticipations().forEach(p -> requests.lockPair(event.getMeet().getId(), p.getAthlete().getId())
                .ifPresent(AvailabilityRequest::proposalChanged));
    }

    /** Idempotent data migration for assignments created before this feature was installed. */
    @Transactional
    public void initializeExistingAssignments() {
        for (var pair : assignments.findMeetAthletePairs()) {
            Long meetId = (Long) pair[0], athleteId = (Long) pair[1];
            lockMeet(meetId);
            if (requests.lockPair(meetId, athleteId).isEmpty()) requests.save(new AvailabilityRequest(
                    meets.getReferenceById(meetId), athletes.getReferenceById(athleteId),
                    Math.toIntExact(assignments.countByEventMeetIdAndAthleteId(meetId, athleteId))));
        }
    }

    @Transactional(readOnly = true)
    public List<RequestView> forMeet(Long meetId) {
        if (!meets.existsById(meetId)) throw new MeetService.MeetNotFoundException(meetId);
        return requests.findByMeetIdAndAssignmentCountGreaterThanOrderByAthleteLastNameAscAthleteFirstNameAscIdAsc(meetId, 0)
                .stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public List<RequestView> upcomingForAthlete(Long athleteId) {
        return forAthlete(athleteId, MeetView.UPCOMING);
    }
    @Transactional(readOnly = true)
    public List<RequestView> forAthlete(Long athleteId, MeetView view) {
        LocalDate today = LocalDate.now(clock);
        return requests.findByAthleteIdAndAssignmentCountGreaterThan(athleteId, 0).stream()
                .filter(r -> view == MeetView.ALL || r.getMeet().getEndDate() != null &&
                        (view == MeetView.UPCOMING ? MeetView.isUpcoming(r.getMeet(), today) : r.getMeet().getEndDate().isBefore(today)))
                .sorted(Comparator.comparing((AvailabilityRequest r) -> r.getMeet().getStartDate(), Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(AvailabilityRequest::getId))
                .map(this::view).toList();
    }
    @Transactional(readOnly = true)
    public RequestView nominatedMeet(Long athleteId, Long meetId) {
        return forAthlete(athleteId, MeetView.ALL).stream().filter(r -> r.meetId().equals(meetId)).findFirst().orElseThrow(AvailabilityService::notFound);
    }

    @Transactional(readOnly = true)
    public RequestView get(Long id, Long scopedAthleteId) { return view(scoped(id, scopedAthleteId)); }

    @Transactional(readOnly = true)
    public List<ResponseAudit> history(Long id) {
        scoped(id, null);
        return responses.findByRequestIdOrderByIdDesc(id).stream().map(ResponseAudit::from).toList();
    }

    @Transactional
    public RequestView replyFromPortal(Long id, StaffPrincipal principal, PortalReply reply) {
        return respond(id, principal.athleteId(), reply.status(), reply.version(), reply.assignmentRevision(),
                Channel.PORTAL, null, principal.id(), null);
    }

    @Transactional
    public RequestView recordExternalResponse(Long id, Long staffUserId, StaffReply reply) {
        if (reply.communicationMethod() == null || reply.communicationMethod() == Channel.PORTAL)
            throw new IllegalArgumentException("Choose an external communication method");
        if (reply.note() != null && reply.note().length() > 1000) throw new IllegalArgumentException("Note must be 1000 characters or fewer");
        return respond(id, null, reply.status(), reply.version(), reply.assignmentRevision(),
                reply.communicationMethod(), staffUserId, null, reply.note());
    }

    private RequestView respond(Long id, Long scopedAthleteId, Answer answer, Long version, Long revision,
                                Channel channel, Long staffUserId, Long athleteUserId, String note) {
        if (answer == null || version == null || revision == null) throw new IllegalArgumentException("Status, version and assignmentRevision are required");
        Long meetId = requests.findMeetId(id).orElseThrow(AvailabilityService::notFound);
        lockMeet(meetId);
        var request = requests.lockById(id).orElseThrow(AvailabilityService::notFound);
        requireScope(request, scopedAthleteId);
        if (!request.isActive() || !MeetView.canRespond(request.getMeet(), clock))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Availability responses are closed for this request");
        if (request.getAssignmentRevision() != revision)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This request changed. Reload the assigned events and confirm again");
        if (request.getVersion() != version) {
            var previous = responses.findFirstByRequestIdOrderByIdDesc(id);
            if (previous.filter(r -> r.getAssignmentRevision() == revision && r.isRetry(version, answer, channel, staffUserId, athleteUserId, note)).isPresent())
                return view(request);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This request changed. Reload the assigned events and confirm again");
        }
        Instant at = clock.instant();
        try {
        responses.save(new AvailabilityResponse(request, answer, channel, at, staffUserId, athleteUserId, note));
        request.respond(answer, at);
        requests.flush(); // Return the committed version required by the next confirmation.
        } catch (org.springframework.dao.DataAccessException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Availability could not be saved. Your previous response is unchanged; reload and retry");
        }
        return view(request);
    }

    private AvailabilityRequest scoped(Long id, Long athleteId) {
        var request = requests.findById(id).orElseThrow(AvailabilityService::notFound);
        requireScope(request, athleteId); return request;
    }
    private void requireScope(AvailabilityRequest request, Long athleteId) {
        if (athleteId != null && (!athleteId.equals(request.getAthlete().getId()) || !request.isActive())) throw notFound();
    }
    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Availability request not found");
    }
    private RequestView view(AvailabilityRequest request) {
        var athlete = request.getAthlete(); var meet = request.getMeet();
        var events = assignments.findByEventMeetIdAndAthleteIdOrderByEventEventDateAscEventIdAsc(meet.getId(), athlete.getId()).stream()
                .map(p -> new AssignedEvent(p.getEvent().getId(), p.getEvent().getName(), p.getEvent().getCategory().getId(),
                        p.getEvent().getCategory().getName(), p.getEvent().getEventDate())).toList();
        return new RequestView(request.getId(), athlete.getId(), athlete.getFirstName(), athlete.getLastName(), meet.getId(),
                meet.getName(), meet.getStartDate(), meet.getEndDate(), meet.getLocation(), request.isActive(),
                request.isActive() && MeetView.canRespond(meet, clock), request.status(), request.getVersion(),
                request.getAssignmentRevision(), request.getLastAnswer(), request.getAnsweredRevision(), request.getRespondedAt(), events,
                MeetView.responseDeadline(meet, clock), clock.getZone().getId());
    }

    @Transactional
    public void deleteForMeet(Long meetId) {
        lockMeet(meetId); responses.deleteByRequestMeetId(meetId); requests.deleteByMeetId(meetId);
    }
    @Transactional
    public void deleteForAthlete(Long athleteId) {
        responses.deleteByRequestAthleteId(athleteId); requests.deleteByAthleteId(athleteId);
    }
}
