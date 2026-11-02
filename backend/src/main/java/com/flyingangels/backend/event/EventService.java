package com.flyingangels.backend.event;

import com.flyingangels.backend.athlete.Athlete;
import com.flyingangels.backend.athlete.AthleteRepository;
import com.flyingangels.backend.athlete.AthleteService.AthleteNotFoundException;
import com.flyingangels.backend.athlete.AthleteEvent;
import com.flyingangels.backend.meet.Meet;
import com.flyingangels.backend.meet.MeetRepository;
import com.flyingangels.backend.meet.MeetService.MeetNotFoundException;
import com.flyingangels.backend.event.dto.EventRequest;
import com.flyingangels.backend.event.dto.EventResultRequest;
import com.flyingangels.backend.event.dto.EventParticipantRequest;
import com.flyingangels.backend.event.dto.ParticipantResultRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import com.flyingangels.backend.event.dto.*;
import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class EventService {

	private final EventRepository eventRepository;
	private final EventCategoryService categoryService;
	private final MeetRepository meetRepository;
	private final EventResultRepository resultRepository;
	private final AthleteRepository athleteRepository;
	private final EventParticipationRepository participationRepository;

	@Transactional(readOnly = true)
	public List<Event> list() {
		return eventRepository.findAllByOrderByEventDateDescNameAsc();
	}

	@Transactional(readOnly = true)
	public Event get(Long id) {
		return eventRepository.findById(id).orElseThrow(() -> new EventNotFoundException(id));
	}

	@Transactional
	public Event create(EventRequest request) {
		Meet meet = meetRepository.findById(request.meetId()).orElseThrow(() -> new MeetNotFoundException(request.meetId()));
		validateEventDate(request, meet);
		return eventRepository.save(new Event(request, categoryService.resolve(request.categoryId(),
				request.categoryName(), request.rankingOrder()), meet));
	}

	@Transactional
	public Event update(Long id, EventRequest request) {
		Event event = get(id);
		Meet meet = meetRepository.findById(request.meetId()).orElseThrow(() -> new MeetNotFoundException(request.meetId()));
		validateEventDate(request, meet);
		event.updateFrom(request, categoryService.resolve(request.categoryId(), request.categoryName(),
				request.rankingOrder()), meet);
		return eventRepository.save(event);
	}

	@Transactional
	public void delete(Long id) {
		eventRepository.delete(get(id));
	}

	private void validateEventDate(EventRequest request, Meet meet) {
		java.time.LocalDate lastDate = meet.getEndDate() == null ? meet.getMeetDate() : meet.getEndDate();
		if (request.eventDate() == null || request.eventDate().isBefore(meet.getMeetDate()) || request.eventDate().isAfter(lastDate)) {
			throw new IllegalArgumentException("Event date must be within the meet's start and end dates");
		}
	}

    @Transactional
    public EventResult addResult(Long eventId, EventResultRequest request) {
        Event event = get(eventId);
        if (request.clientId() != null && !request.clientId().isBlank()) {
            var existing = resultRepository.findByClientId(request.clientId());
            if (existing.isPresent()) {
                EventResult result = existing.get();
                if (result.getEvent() == null || !result.getEvent().getId().equals(eventId) || !result.getAthlete().getId().equals(request.athleteId()))
                    throw new IllegalArgumentException("This request ID belongs to another participation");
                return result;
            }
        }
        EventParticipation participant = ensureParticipant(event, request.athleteId());
        EventResult result = new EventResult(event, event.getCategory(), participant.getAthlete(), request.result(), request.score());
        result.setClientId(request.clientId() == null || request.clientId().isBlank() ? null : request.clientId());
        event.addResult(result);
        return resultRepository.save(result);
    }

    private EventParticipation ensureParticipant(Event event, Long athleteId) {
        var existing = participationRepository.findByEventIdAndAthleteId(event.getId(), athleteId);
        if (existing.isPresent()) return existing.get();
        Athlete athlete = athleteRepository.findById(athleteId).orElseThrow(() -> new AthleteNotFoundException(athleteId));
        if (athlete.isArchived()) throw new IllegalArgumentException("Restore this athlete before adding them to an event");
        EventParticipation participant = participationRepository.save(new EventParticipation(event, athlete));
        event.getParticipations().add(participant);
        return participant;
    }

    @Transactional
    public EventParticipation assignParticipant(Long eventId, EventParticipantRequest request) {
        Event event = get(eventId);
        if (participationRepository.findByEventIdAndAthleteId(eventId, request.athleteId()).isPresent())
            throw new DuplicateResultException(eventId, request.athleteId());
        return ensureParticipant(event, request.athleteId());
    }

    @Transactional
    public List<EventParticipation> assignBatch(Long eventId, List<Long> ids) {
        Event event = get(eventId);
        return ids.stream().distinct().map(id -> ensureParticipant(event, id)).toList();
    }

    @Transactional(readOnly = true)
    public List<EventParticipation> participants(Long eventId) {
        get(eventId);
        return participationRepository.findByEventIdOrderByAthleteLastNameAscAthleteFirstNameAsc(eventId);
    }

    @Transactional
    public void removeParticipant(Long eventId, Long athleteId) {
        Event event = get(eventId);
        EventParticipation participant = participationRepository.findByEventIdAndAthleteId(eventId, athleteId)
                .orElseThrow(() -> new IllegalArgumentException("Athlete is not assigned to this event"));
        event.getResults().removeIf(result -> result.getAthlete().getId().equals(athleteId));
        event.getParticipations().remove(participant);
    }

    @Transactional
    public EventResult recordParticipantResult(Long eventId, Long athleteId, ParticipantResultRequest request) {
        if (participationRepository.findByEventIdAndAthleteId(eventId, athleteId).isEmpty())
            throw new IllegalArgumentException("Assign the athlete before recording a result");
        return addResult(eventId, new EventResultRequest(athleteId, request.result(), request.score()));
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
            else addResult(eventId, new EventResultRequest(athleteId, attempt.result(), attempt.score(), attempt.clientId()));
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

	@Transactional
	public void recordImportedResults(Athlete athlete) {
		for (AthleteEvent imported : athlete.getEvents()) {
			EventCategory category = categoryService.resolve(null, imported.getEventName(), RankingOrder.ASC);
			if (!resultRepository.existsByEventIsNullAndCategoryIdAndAthleteIdAndResult(
					category.getId(), athlete.getId(), imported.getResult())) {
				resultRepository.save(new EventResult(null, category, athlete, imported.getResult(),
						parseImportedScore(imported.getResult())));
			}
		}
	}

	private BigDecimal parseImportedScore(String result) {
		String normalized = result.trim().replaceAll("[qQ]$", "").replaceAll("[mM]$", "").trim();
		if (normalized.contains(":")) {
			return null;
		}
		try {
			return new BigDecimal(normalized);
		} catch (NumberFormatException exception) {
			return null;
		}
	}

	@Transactional
	public EventResult updateResult(Long eventId, Long resultId, EventResultRequest request) {
		EventResult result = resultRepository.findByIdAndEventId(resultId, eventId)
				.orElseThrow(() -> new EventResultNotFoundException(resultId, eventId));

		Athlete athlete = athleteRepository.findById(request.athleteId())
				.orElseThrow(() -> new AthleteNotFoundException(request.athleteId()));
		if (athlete.isArchived() && !result.getAthlete().getId().equals(athlete.getId())) {
			throw new IllegalArgumentException("Restore this athlete before adding them to an event");
		}
		ensureParticipant(result.getEvent(), athlete.getId());
		result.update(athlete, request.result(), request.score());
		return resultRepository.save(result);
	}

	@Transactional
	public void deleteResult(Long eventId, Long resultId) {
		EventResult result = resultRepository.findByIdAndEventId(resultId, eventId)
				.orElseThrow(() -> new EventResultNotFoundException(resultId, eventId));
		resultRepository.delete(result);
	}

	@Transactional(readOnly = true)
	public List<EventResult> leaderboard(Long eventId) {
		Event event = get(eventId);
		Comparator<EventResult> comparator = Comparator.comparing(EventResult::getScore)
				.thenComparing(result -> result.getAthlete().getLastName(), String.CASE_INSENSITIVE_ORDER)
				.thenComparing(result -> result.getAthlete().getFirstName(), String.CASE_INSENSITIVE_ORDER);
		if (event.getRankingOrder() == RankingOrder.DESC) {
			comparator = comparator.reversed();
		}
        Map<Long, EventResult> best = new HashMap<>();
        for (EventResult result : event.getResults()) if (result.getScore() != null) {
            EventResult previous = best.get(result.getAthlete().getId());
            if (previous == null || comparator.compare(result, previous) < 0) best.put(result.getAthlete().getId(), result);
        }
        return best.values().stream().sorted(comparator).toList();
	}

	public static class EventNotFoundException extends RuntimeException {
		public EventNotFoundException(Long id) { super("Event not found: " + id); }
	}


	public static class EventResultNotFoundException extends RuntimeException {
		public EventResultNotFoundException(Long resultId, Long eventId) {
			super("Result " + resultId + " not found for event " + eventId);
		}
	}

	public static class DuplicateResultException extends RuntimeException {
		public DuplicateResultException(Long eventId, Long athleteId) {
			super("Athlete " + athleteId + " is already assigned to event " + eventId);
		}
	}

}
