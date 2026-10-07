package com.flyingangels.backend.meet;

import com.flyingangels.backend.meet.dto.MeetProfileResponse;
import com.flyingangels.backend.meet.dto.MeetRequest;
import com.flyingangels.backend.meet.dto.MeetResponse;
import com.flyingangels.backend.event.dto.EventResponse;
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
public class MeetController {

	private final MeetService meetService;

	@GetMapping("/api/meets")
	public List<MeetResponse> list() {
		return meetService.list().stream().map(MeetResponse::from).toList();
	}

	@GetMapping("/api/meets/{id}")
	public MeetProfileResponse get(@PathVariable Long id) {
		return MeetProfileResponse.from(meetService.get(id));
	}

	@GetMapping("/api/meets/{meetId}/events/{eventId}")
	public EventResponse event(@PathVariable Long meetId, @PathVariable Long eventId) {
		return EventResponse.from(meetService.event(meetId, eventId));
	}

	@PostMapping("/api/meets")
	@ResponseStatus(HttpStatus.CREATED)
	public MeetResponse create(@Valid @RequestBody MeetRequest request) {
		return MeetResponse.from(meetService.create(request));
	}

	@PutMapping("/api/meets/{id}")
	public MeetResponse update(@PathVariable Long id, @Valid @RequestBody MeetRequest request) {
		return MeetResponse.from(meetService.update(id, request));
	}

	@DeleteMapping("/api/meets/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable Long id) {
		meetService.delete(id);
	}
}