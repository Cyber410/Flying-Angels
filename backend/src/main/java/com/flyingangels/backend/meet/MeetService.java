package com.flyingangels.backend.meet;

import com.flyingangels.backend.event.EventRepository;
import com.flyingangels.backend.event.Event;
import com.flyingangels.backend.event.EventService.EventNotFoundException;
import com.flyingangels.backend.meet.dto.MeetRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MeetService {

	private final MeetRepository repository;
	private final EventRepository eventRepository;
    private final com.flyingangels.backend.note.AthleteNoteRepository noteRepository;
    private final com.flyingangels.backend.availability.AvailabilityService availability;

	@Transactional(readOnly = true)
	public List<Meet> list() {
		return repository.findAllByOrderByMeetDateDescNameAsc();
	}

	@Transactional(readOnly = true)
	public Meet get(Long id) {
		return repository.findById(id).orElseThrow(() -> new MeetNotFoundException(id));
	}

    @Transactional(readOnly = true)
    public com.flyingangels.backend.meet.dto.MeetProfileResponse profile(Long id) {
        return com.flyingangels.backend.meet.dto.MeetProfileResponse.from(get(id), availability.forMeet(id));
    }

	@Transactional(readOnly = true)
	public Event event(Long meetId, Long eventId) {
		if (!repository.existsById(meetId)) {
			throw new MeetNotFoundException(meetId);
		}
		return eventRepository.findByIdAndMeetId(eventId, meetId)
				.orElseThrow(() -> new EventNotFoundException(eventId));
	}

	@Transactional
	public Meet create(MeetRequest request) {
		return repository.save(new Meet(request));
	}

	@Transactional
	public Meet update(Long id, MeetRequest request) {
		Meet meet = get(id);
		availability.lockMeet(id);
		if (!request.isDateRangeValid() || meet.getEvents().stream().anyMatch(e->e.getEventDate().isBefore(request.effectiveStartDate()) || e.getEventDate().isAfter(request.effectiveEndDate())))
            throw new IllegalArgumentException("Meet dates must include all existing events");
        if (!java.util.Objects.equals(meet.getStartDate(), request.effectiveStartDate()) || !java.util.Objects.equals(meet.getEndDate(), request.effectiveEndDate())
                || !java.util.Objects.equals(meet.getStartTime(), request.startTime() == null ? java.time.LocalTime.MIDNIGHT : request.startTime())
                || !java.util.Objects.equals(meet.getLocation(), request.location())) availability.proposalChangedForMeet(id);
        meet.updateFrom(request);
		return repository.save(meet);
	}

	@Transactional
	public void delete(Long id) {
		var meet=get(id); availability.deleteForMeet(id);
        meet.getEvents().forEach(e->noteRepository.detachEvent(e.getId())); repository.delete(meet);
	}

	public static class MeetNotFoundException extends RuntimeException {
		public MeetNotFoundException(Long id) { super("Meet not found: " + id); }
	}
}
