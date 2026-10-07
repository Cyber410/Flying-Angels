package com.flyingangels.backend.event;

import com.flyingangels.backend.event.dto.EventCategoryRequest;
import com.flyingangels.backend.event.dto.EventCategoryResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class EventCategoryController {

	private final EventCategoryService categoryService;

	@GetMapping("/api/event-categories")
	public List<EventCategoryResponse> list() {
		return categoryService.list().stream().map(EventCategoryResponse::from).toList();
	}

	@PostMapping("/api/event-categories")
	@ResponseStatus(HttpStatus.CREATED)
	public EventCategoryResponse create(@Valid @RequestBody EventCategoryRequest request) {
		return EventCategoryResponse.from(categoryService.create(request));
	}
}