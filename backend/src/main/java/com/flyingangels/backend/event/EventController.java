package com.flyingangels.backend.event;

import com.flyingangels.backend.event.dto.EventProfileResponse;
import com.flyingangels.backend.event.dto.EventRequest;
import com.flyingangels.backend.event.dto.EventResponse;
import com.flyingangels.backend.event.dto.EventResultRequest;
import com.flyingangels.backend.event.dto.EventResultResponse;
import com.flyingangels.backend.event.dto.EventParticipantRequest;
import com.flyingangels.backend.event.dto.ParticipantResultRequest;
import com.flyingangels.backend.event.dto.*;
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
	public EventParticipationResponse assignParticipant(@PathVariable Long id,
			@Valid @RequestBody EventParticipantRequest request) {
		return EventParticipationResponse.from(eventService.assignParticipant(id, request));
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

	@GetMapping("/api/events/{id}/participants")
	public List<EventParticipationResponse> participants(@PathVariable Long id) {
		return eventService.participants(id).stream().map(EventParticipationResponse::from).toList();
	}
	@PostMapping("/api/events/{id}/participants/bulk")
	@ResponseStatus(HttpStatus.CREATED)
	public List<EventParticipationResponse> assignBatch(@PathVariable Long id, @Valid @RequestBody ParticipantBatchRequest request) {
		return eventService.assignBatch(id, request.athleteIds()).stream().map(EventParticipationResponse::from).toList();
	}
	@DeleteMapping("/api/events/{id}/participants/{athleteId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void removeParticipant(@PathVariable Long id, @PathVariable Long athleteId) { eventService.removeParticipant(id, athleteId); }

	@GetMapping("/api/events/{id}/note")
	public java.util.Map<String, String> note(@PathVariable Long id) { return java.util.Map.of("text", eventService.getNote(id, null)); }
	@PutMapping("/api/events/{id}/note")
	public java.util.Map<String, String> note(@PathVariable Long id, @Valid @RequestBody EventNoteRequest request) { return java.util.Map.of("text", eventService.saveNote(id, null, request.text())); }
	@PostMapping("/api/events/{id}/note/migrate-browser-data")
	public java.util.Map<String, String> migrateEventNote(@PathVariable Long id, @Valid @RequestBody EventNoteRequest request) { return java.util.Map.of("text", eventService.migrateEventNote(id, request.text())); }
	@GetMapping("/api/events/{id}/participants/{athleteId}/note")
	public java.util.Map<String, String> performanceNote(@PathVariable Long id, @PathVariable Long athleteId) { return java.util.Map.of("text", eventService.getNote(id, athleteId)); }
	@PutMapping("/api/events/{id}/participants/{athleteId}/note")
	public java.util.Map<String, String> performanceNote(@PathVariable Long id, @PathVariable Long athleteId, @Valid @RequestBody EventNoteRequest request) { return java.util.Map.of("text", eventService.saveNote(id, athleteId, request.text())); }
	@PostMapping("/api/events/{id}/participants/{athleteId}/migrate-browser-data")
	public EventParticipationResponse migrateBrowserData(@PathVariable Long id, @PathVariable Long athleteId, @Valid @RequestBody BrowserDataRequest request) {
		return EventParticipationResponse.from(eventService.migrateBrowserData(id, athleteId, request));
	}
    @GetMapping("/api/events/{id}/results")
    public List<EventResultResponse> results(@PathVariable Long id) { return eventService.get(id).getResults().stream().map(EventResultResponse::from).toList(); }
    @PostMapping("/api/events/{id}/results/bulk") @ResponseStatus(HttpStatus.CREATED)
    public List<EventResultResponse> resultsBulk(@PathVariable Long id,@Valid @RequestBody com.flyingangels.backend.event.dto.ResultBatchRequest request) {
        return eventService.addResults(id,request.results()).stream().map(EventResultResponse::from).toList();
    }
    @PutMapping("/api/events/{id}/results/bulk")
    public List<EventResultResponse> updateResultsBulk(@PathVariable Long id,
            @Valid @RequestBody com.flyingangels.backend.event.dto.ResultUpdateBatchRequest request) {
        return eventService.updateResults(id,request.results()).stream().map(EventResultResponse::from).toList();
    }
}
