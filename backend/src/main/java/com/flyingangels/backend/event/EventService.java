package com.flyingangels.backend.event;

import com.flyingangels.backend.athlete.*;
import com.flyingangels.backend.event.dto.*;
import com.flyingangels.backend.meet.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
public class EventService {
    private final EventRepository eventRepository;
    private final EventCategoryService categoryService;
    private final MeetRepository meetRepository;
    private final EventResultRepository resultRepository;
    private final AthleteRepository athleteRepository;
    private final EventParticipationRepository participationRepository;
    private final com.flyingangels.backend.note.AthleteNoteRepository noteRepository;

    private EventParticipation ensureParticipant(Event event, Long athleteId) {
        var existing = participationRepository.findByEventIdAndAthleteId(event.getId(), athleteId);
        if (existing.isPresent()) return existing.get();
        Athlete athlete = athleteRepository.findById(athleteId).orElseThrow(() -> new AthleteService.AthleteNotFoundException(athleteId));
        if (athlete.isArchived()) throw new IllegalArgumentException("Restore this athlete before adding them to an event");
        EventParticipation participant = participationRepository.save(new EventParticipation(event, athlete));
        event.getParticipations().add(participant);
        return participant;
    }

    @Transactional(readOnly = true)
    public String getNote(Long eventId, Long athleteId) {
        Event event = get(eventId);
        String text = athleteId == null ? event.getNote() : participationRepository.findByEventIdAndAthleteId(eventId, athleteId)
                .orElseThrow(() -> new IllegalArgumentException("Athlete is not assigned to this event")).getNote();
        return text == null ? "" : text;
    }

    @Transactional
    public String saveNote(Long eventId, Long athleteId, String text) {
        Event event = get(eventId);
        if (athleteId == null) event.setNote(text);
        else participationRepository.findByEventIdAndAthleteId(eventId, athleteId)
                .orElseThrow(() -> new IllegalArgumentException("Athlete is not assigned to this event")).setNote(text);
        return text.trim();
    }

    @Transactional
    public EventParticipation migrateBrowserData(Long eventId, Long athleteId, BrowserDataRequest request) {
        Event event = get(eventId);
        EventParticipation participant = ensureParticipant(event, athleteId);
        if (request.note() != null && !request.note().isBlank()) {
            if (participant.getNote() != null && !participant.getNote().isBlank() && !participant.getNote().equals(request.note().trim()))
                throw new IllegalArgumentException("A different shared performance note already exists. Review the saved note manually; it has been preserved.");
            participant.setNote(request.note());
        }
        for (var attempt : request.attempts()) {
            var prior = resultRepository.findByClientId(attempt.clientId());
            if (prior.isPresent()) {
                if (prior.get().getEvent() == null || !prior.get().getEvent().getId().equals(eventId) || !prior.get().getAthlete().getId().equals(athleteId))
                    throw new IllegalArgumentException("This saved attempt belongs to another participation");
                continue;
            }
            var matching = resultRepository.findByEventIdAndAthleteId(eventId, athleteId).stream()
                    .filter(result -> result.getClientId() == null && result.getScore() != null && result.getScore().compareTo(attempt.score()) == 0).findFirst();
            if (matching.isPresent()) matching.get().setClientId(attempt.clientId());
            else addResult(eventId, new EventResultRequest(athleteId, attempt.result(), attempt.score(), null, attempt.clientId()));
        }
        return participant;
    }

    @Transactional
    public String migrateEventNote(Long eventId, String text) {
        get(eventId);
        if (text.isBlank()) return "";
        if (eventRepository.migrateNote(eventId, text.trim()) == 0)
            throw new IllegalArgumentException("A different shared event note already exists. The saved note is preserved for manual review.");
        return text.trim();
    }

    @Transactional(readOnly=true)
    public List<Event> list() { return eventRepository.findAllByOrderByEventDateDescNameAsc(); }
    @Transactional(readOnly=true)
    public Event get(Long id) { return eventRepository.findById(id).orElseThrow(()->new EventNotFoundException(id)); }
    private Meet meet(Long id) { return meetRepository.findById(id).orElseThrow(()->new MeetService.MeetNotFoundException(id)); }
    private Athlete athlete(Long id) { return athleteRepository.findById(id).orElseThrow(()->new AthleteService.AthleteNotFoundException(id)); }
    private void validateDate(Meet meet, java.time.LocalDate date) {
        if (date==null || date.isBefore(meet.getStartDate()) || date.isAfter(meet.getEndDate()))
            throw new IllegalArgumentException("Event date must fall within the meet dates");
    }
    @Transactional
    public Event create(EventRequest request) {
        Meet meet=meet(request.meetId()); validateDate(meet,request.eventDate());
        var category=categoryService.resolve(request.categoryId(),request.categoryName(),request.rankingOrder());
        if (eventRepository.existsByMeetIdAndCategoryId(meet.getId(),category.getId())) throw new DuplicateEventException();
        return eventRepository.save(new Event(request,category,meet));
    }
    @Transactional
    public List<Event> createBatch(Long meetId,List<EventRequest> requests) {
        meet(meetId);
        if (requests.stream().anyMatch(r->!meetId.equals(r.meetId()))) throw new IllegalArgumentException("All events must belong to the target meet");
        return requests.stream().map(this::create).toList();
    }
    @Transactional
    public List<Event> copyEvents(Long targetMeetId, CopyEventsRequest request) {
        Meet target=meet(targetMeetId); meet(request.sourceMeetId());
        if (new HashSet<>(request.eventIds()).size()!=request.eventIds().size()) throw new IllegalArgumentException("Repeated event IDs");
        return request.eventIds().stream().map(id->{
            Event source=eventRepository.findByIdAndMeetId(id,request.sourceMeetId()).orElseThrow(()->new EventNotFoundException(id));
            return create(new EventRequest(source.getName(),target.getStartDate(),target.getLocation(),source.getEventType(),
                source.getExternalUrl(),source.getRankingOrder(),target.getId(),source.getCategory().getId(),null));
        }).toList();
    }
    @Transactional
    public Event update(Long id,EventRequest request) {
        Event event=get(id); Meet meet=meet(request.meetId()); validateDate(meet,request.eventDate());
        var category=categoryService.resolve(request.categoryId(),request.categoryName(),request.rankingOrder());
        if (eventRepository.existsByMeetIdAndCategoryIdAndIdNot(meet.getId(),category.getId(),id)) throw new DuplicateEventException();
        if (!event.getResults().isEmpty() && (!category.getId().equals(event.getCategory().getId()) || !meet.getId().equals(event.getMeet().getId())))
            throw new IllegalArgumentException("An event with results cannot change meet or category");
        event.updateFrom(request,category,meet); return eventRepository.save(event);
    }
    @Transactional
    public void delete(Long id) { var event=get(id); noteRepository.detachEvent(id); eventRepository.delete(event); }
    @Transactional
    public EventParticipation assignParticipant(Long eventId,EventParticipantRequest request) {
        Event event=get(eventId); Athlete athlete=athlete(request.athleteId());
        if (participationRepository.existsByEventIdAndAthleteId(eventId,athlete.getId())) throw new DuplicateResultException(eventId,athlete.getId());
        return ensureParticipant(event,athlete.getId());
    }
    @Transactional
    public List<EventParticipation> assignBatch(Long eventId,List<Long> athleteIds) {
        if (new HashSet<>(athleteIds).size()!=athleteIds.size()) throw new IllegalArgumentException("Repeated athlete IDs");
        Event event=get(eventId);
        return athleteIds.stream().map(id->ensureParticipant(event,id)).toList();
    }
    @Transactional(readOnly=true)
    public List<EventParticipation> participants(Long eventId) { get(eventId); return participationRepository.findByEventIdOrderByAthleteLastNameAscAthleteFirstNameAsc(eventId); }
    @Transactional
    public void removeParticipant(Long eventId,Long athleteId) {
        Event event=get(eventId);
        var p=participationRepository.findByEventIdAndAthleteId(eventId,athleteId).orElseThrow(()->new ParticipantNotFoundException());
        event.getResults().removeIf(r->r.getAthlete().getId().equals(athleteId));
        p.getEvent().getParticipations().remove(p);
        participationRepository.delete(p);
    }
    @Transactional
    public EventResult addResult(Long eventId,EventResultRequest request) {
        Event event=get(eventId);
        if (request.clientId() != null && !request.clientId().isBlank()) {
            var existing = resultRepository.findByClientId(request.clientId());
            if (existing.isPresent()) {
                EventResult result = existing.get();
                if (result.getEvent() == null || !result.getEvent().getId().equals(eventId) || !result.getAthlete().getId().equals(request.athleteId()))
                    throw new IllegalArgumentException("This request ID belongs to another participation");
                return result;
            }
        }
        Athlete athlete=ensureParticipant(event,request.athleteId()).getAthlete();
        var measurement=measurement(event,request);
        var result=new EventResult(event,event.getCategory(),athlete,request.result(),measurement.score());
        result.setClientId(request.clientId()==null || request.clientId().isBlank()?null:request.clientId());
        result.setMeasurement(measurement.unit(),measurement.status()); event.addResult(result);
        return resultRepository.save(result);
    }
    private ResultValues.Measurement measurement(Event event,EventResultRequest request) {
        var expected=event.getCategory().getUnit();
        if (request.unit()!=null && request.unit()!=expected) throw new IllegalArgumentException("Unit must be " + expected);
        return ResultValues.parse(request.result(),expected,request.score());
    }
    @Transactional
    public List<EventResult> addResults(Long eventId,List<EventResultRequest> requests) { return requests.stream().map(r->addResult(eventId,r)).toList(); }
    /** Edit All Results is atomic and changes existing attempts without changing their identity. */
    @Transactional
    public List<EventResult> updateResults(Long eventId,List<ResultUpdateRequest> requests) {
        Event event = get(eventId);
        if (requests == null || requests.isEmpty()) throw new IllegalArgumentException("Select at least one result to update");
        Set<Long> selected = new HashSet<>();
        List<ResultCorrection> corrections = new ArrayList<>();
        for (var request : requests) {
            if (request == null || request.resultId() == null || !selected.add(request.resultId()))
                throw new IllegalArgumentException("Result IDs must be supplied once each");
            var result = resultRepository.findByIdAndEventId(request.resultId(),eventId)
                    .orElseThrow(() -> new EventResultNotFoundException(request.resultId(),eventId));
            var value = measurement(event,new EventResultRequest(result.getAthlete().getId(),request.result(),request.score(),request.unit()));
            corrections.add(new ResultCorrection(result,request,value));
        }
        for (var correction : corrections) {
            var result = correction.result();
            result.update(result.getAthlete(),correction.request().result(),correction.measurement().score());
            result.setMeasurement(correction.measurement().unit(),correction.measurement().status());
        }
        return resultRepository.saveAll(corrections.stream().map(ResultCorrection::result).toList());
    }
    private record ResultCorrection(EventResult result,ResultUpdateRequest request,ResultValues.Measurement measurement) {}
    /** Legacy endpoint now appends an attempt; explicit result-ID PUT remains the correction operation. */
    @Transactional
    public EventResult recordParticipantResult(Long eventId,Long athleteId,ParticipantResultRequest request) {
        if (!participationRepository.existsByEventIdAndAthleteId(eventId,athleteId)) throw new ParticipantNotFoundException();
        return addResult(eventId,new EventResultRequest(athleteId,request.result(),request.score(),request.unit()));
    }
    @Transactional
    public EventResult updateResult(Long eventId,Long resultId,EventResultRequest request) {
        var result=resultRepository.findByIdAndEventId(resultId,eventId).orElseThrow(()->new EventResultNotFoundException(resultId,eventId));
        var m=measurement(result.getEvent(),request); var athlete=athlete(request.athleteId());
        if (!participationRepository.existsByEventIdAndAthleteId(eventId,athlete.getId())) assignParticipant(eventId,new EventParticipantRequest(athlete.getId()));
        result.update(athlete,request.result(),m.score()); result.setMeasurement(m.unit(),m.status()); return resultRepository.save(result);
    }
    @Transactional
    public void deleteResult(Long eventId,Long resultId) {
        resultRepository.delete(resultRepository.findByIdAndEventId(resultId,eventId).orElseThrow(()->new EventResultNotFoundException(resultId,eventId)));
    }
    @Transactional
    public void recordImportedResults(Athlete athlete) { recordImportedResults(athlete,athlete.getEvents()); }
    @Transactional
    public void recordImportedResults(Athlete athlete,List<AthleteEvent> incoming) {
        for (var imported:incoming) {
            var order=ResultValues.unitFor(imported.getEventName(),RankingOrder.ASC)==ResultUnit.SECONDS?RankingOrder.ASC:RankingOrder.DESC;
            var category=categoryService.resolve(null,imported.getEventName(),order);
            if (!resultRepository.existsByEventIsNullAndCategoryIdAndAthleteIdAndResult(category.getId(),athlete.getId(),imported.getResult())) {
                var unit=category.getUnit();
                ResultValues.Measurement m;
                try { m=ResultValues.parse(imported.getResult(),unit,null); }
                catch (IllegalArgumentException ex) { m=new ResultValues.Measurement(null,unit,ResultStatus.UNPARSED); }
                var r=new EventResult(null,category,athlete,imported.getResult(),m.score()); r.setMeasurement(m.unit(),m.status()); resultRepository.save(r);
            }
        }
    }
    @Transactional(readOnly=true)
    public List<EventResult> leaderboard(Long eventId) {
        var event=get(eventId);
        Comparator<EventResult> byScore=Comparator.comparing(EventResult::getScore);
        if (event.getRankingOrder()==RankingOrder.DESC) byScore=byScore.reversed();
        var comparator=byScore.thenComparing(r->r.getAthlete().getLastName(),String.CASE_INSENSITIVE_ORDER)
            .thenComparing(r->r.getAthlete().getFirstName(),String.CASE_INSENSITIVE_ORDER);
        // One best attempt per athlete; tied scores retain deterministic name ordering.
        Map<Long,EventResult> best=new HashMap<>();
        for (var r:event.getResults()) if (r.getScore()!=null && r.getStatus()==ResultStatus.VALID)
            best.merge(r.getAthlete().getId(),r,(a,b)->comparator.compare(a,b)<=0?a:b);
        return best.values().stream().sorted(comparator).toList();
    }
    public static class EventNotFoundException extends RuntimeException { public EventNotFoundException(Long id){super("Event not found: "+id);} }
    public static class EventResultNotFoundException extends RuntimeException { public EventResultNotFoundException(Long id,Long event){super("Result "+id+" not found for event "+event);} }
    public static class DuplicateResultException extends RuntimeException { public DuplicateResultException(Long event,Long athlete){super("Athlete "+athlete+" already assigned to event "+event);} }
    public static class DuplicateEventException extends RuntimeException { public DuplicateEventException(){super("Event category already exists in this meet");} }
    public static class ParticipantNotFoundException extends RuntimeException { public ParticipantNotFoundException(){super("Participant not found");} }
}
