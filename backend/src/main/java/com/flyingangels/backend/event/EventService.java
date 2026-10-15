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
import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class EventService {

	private final EventRepository eventRepository;
	private final EventCategoryService categoryService;
	private final MeetRepository meetRepository;
	private final EventResultRepository resultRepository;
	private final AthleteRepository athleteRepository;

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
		return eventRepository.save(new Event(request, categoryService.resolve(request.categoryId(),
				request.categoryName(), request.rankingOrder()), meet));
	}

	@Transactional
	public Event update(Long id, EventRequest request) {
		Event event = get(id);
		Meet meet = meetRepository.findById(request.meetId()).orElseThrow(() -> new MeetNotFoundException(request.meetId()));
		event.updateFrom(request, categoryService.resolve(request.categoryId(), request.categoryName(),
				request.rankingOrder()), meet);
		return eventRepository.save(event);
	}

	@Transactional
	public void delete(Long id) {
		eventRepository.delete(get(id));
	}

	@Transactional
	public EventResult addResult(Long eventId, EventResultRequest request) {
		Event event = get(eventId);
		Athlete athlete = athleteRepository.findById(request.athleteId())
				.orElseThrow(() -> new AthleteNotFoundException(request.athleteId()));
		if (resultRepository.existsByEventIdAndAthleteId(eventId, request.athleteId())) {
			throw new DuplicateResultException(eventId, request.athleteId());
		}
		EventResult result = new EventResult(event, event.getCategory(), athlete, request.result(), request.score());
		event.addResult(result);
		return resultRepository.save(result);
	}

	@Transactional
	public EventResult assignParticipant(Long eventId, EventParticipantRequest request) {
		Event event = get(eventId);
		Athlete athlete = athleteRepository.findById(request.athleteId())
				.orElseThrow(() -> new AthleteNotFoundException(request.athleteId()));
		if (resultRepository.existsByEventIdAndAthleteId(eventId, request.athleteId())) {
			throw new DuplicateResultException(eventId, request.athleteId());
		}
		return resultRepository.save(new EventResult(event, event.getCategory(), athlete, null, null));
	}

	@Transactional
	public EventResult recordParticipantResult(Long eventId, Long athleteId, ParticipantResultRequest request) {
		EventResult result = resultRepository.findByEventIdAndAthleteId(eventId, athleteId)
				.orElseThrow(() -> new EventResultNotFoundException(athleteId, eventId));
		result.update(result.getAthlete(), request.result(), request.score());
		return resultRepository.save(result);
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
		if (!result.getAthlete().getId().equals(request.athleteId())
				&& resultRepository.existsByEventIdAndAthleteId(eventId, request.athleteId())) {
			throw new DuplicateResultException(eventId, request.athleteId());
		}
		Athlete athlete = athleteRepository.findById(request.athleteId())
				.orElseThrow(() -> new AthleteNotFoundException(request.athleteId()));
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
		return event.getResults().stream().filter(result -> result.getScore() != null).sorted(comparator).toList();
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