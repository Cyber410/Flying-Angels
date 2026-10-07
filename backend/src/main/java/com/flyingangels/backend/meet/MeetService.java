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

	@Transactional(readOnly = true)
	public List<Meet> list() {
		return repository.findAllByOrderByMeetDateDescNameAsc();
	}

	@Transactional(readOnly = true)
	public Meet get(Long id) {
		return repository.findById(id).orElseThrow(() -> new MeetNotFoundException(id));
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
		meet.updateFrom(request);
		return repository.save(meet);
	}

	@Transactional
	public void delete(Long id) {
		repository.delete(get(id));
	}

	public static class MeetNotFoundException extends RuntimeException {
		public MeetNotFoundException(Long id) { super("Meet not found: " + id); }
	}
}