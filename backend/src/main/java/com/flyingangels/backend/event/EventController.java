package com.flyingangels.backend.event;

import com.flyingangels.backend.event.dto.EventProfileResponse;
import com.flyingangels.backend.event.dto.EventRequest;
import com.flyingangels.backend.event.dto.EventResponse;
import com.flyingangels.backend.event.dto.EventResultRequest;
import com.flyingangels.backend.event.dto.EventResultResponse;
import com.flyingangels.backend.event.dto.EventParticipantRequest;
import com.flyingangels.backend.event.dto.ParticipantResultRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class EventController {

	private final EventService eventService;

	@GetMapping("/api/events")
	public List<EventResponse> list() {
		return eventService.list().stream().map(EventResponse::from).toList();
	}

	@GetMapping("/api/events/{id}")
	public EventProfileResponse get(@PathVariable Long id) {
		return EventProfileResponse.from(eventService.get(id));
	}

	@PostMapping("/api/events")
	@ResponseStatus(HttpStatus.CREATED)
	public EventResponse create(@Valid @RequestBody EventRequest request) {
		return EventResponse.from(eventService.create(request));
	}

	@PutMapping("/api/events/{id}")
	public EventResponse update(@PathVariable Long id, @Valid @RequestBody EventRequest request) {
		return EventResponse.from(eventService.update(id, request));
	}

	@DeleteMapping("/api/events/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable Long id) {
		eventService.delete(id);
	}

	@GetMapping("/api/events/{id}/leaderboard")
	public List<EventResultResponse> leaderboard(@PathVariable Long id) {
		return eventService.leaderboard(id).stream().map(EventResultResponse::from).toList();
	}

	@PostMapping("/api/events/{id}/results")
	@ResponseStatus(HttpStatus.CREATED)
	public EventResultResponse addResult(@PathVariable Long id, @Valid @RequestBody EventResultRequest request) {
		return EventResultResponse.from(eventService.addResult(id, request));
	}

	@PostMapping("/api/events/{id}/participants")
	@ResponseStatus(HttpStatus.CREATED)
	public EventResultResponse assignParticipant(@PathVariable Long id,
			@Valid @RequestBody EventParticipantRequest request) {
		return EventResultResponse.from(eventService.assignParticipant(id, request));
	}

	@PutMapping("/api/events/{id}/participants/{athleteId}/result")
	public EventResultResponse recordParticipantResult(@PathVariable Long id, @PathVariable Long athleteId,
			@Valid @RequestBody ParticipantResultRequest request) {
		return EventResultResponse.from(eventService.recordParticipantResult(id, athleteId, request));
	}

	@PutMapping("/api/events/{id}/results/{resultId}")
	public EventResultResponse updateResult(@PathVariable Long id, @PathVariable Long resultId,
			@Valid @RequestBody EventResultRequest request) {
		return EventResultResponse.from(eventService.updateResult(id, resultId, request));
	}

	@DeleteMapping("/api/events/{id}/results/{resultId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteResult(@PathVariable Long id, @PathVariable Long resultId) {
		eventService.deleteResult(id, resultId);
	}
}