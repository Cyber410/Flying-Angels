package com.flyingangels.backend.event;

import com.flyingangels.backend.event.dto.EventCategoryRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EventCategoryService {

	private final EventCategoryRepository repository;

	@Transactional(readOnly = true)
	public List<EventCategory> list() {
		return repository.findAllByOrderByNameAsc();
	}

	@Transactional(readOnly = true)
	public EventCategory get(Long id) {
		return repository.findById(id).orElseThrow(() -> new EventCategoryNotFoundException(id));
	}

	@Transactional
	public EventCategory create(EventCategoryRequest request) {
		if (repository.findByNameIgnoreCase(request.name().trim()).isPresent()) {
			throw new DuplicateEventCategoryException(request.name());
		}
		return repository.save(new EventCategory(request.name(), request.rankingOrder()));
	}

	@Transactional
	public EventCategory resolve(Long id, String name, RankingOrder rankingOrder) {
		if (id != null) {
			return get(id);
		}
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("Either categoryId or categoryName is required");
		}
		return repository.findByNameIgnoreCase(name.trim())
				.orElseGet(() -> repository.save(new EventCategory(name, rankingOrder)));
	}

	public static class EventCategoryNotFoundException extends RuntimeException {
		public EventCategoryNotFoundException(Long id) { super("Event category not found: " + id); }
	}

	public static class DuplicateEventCategoryException extends RuntimeException {
		public DuplicateEventCategoryException(String name) { super("Event category already exists: " + name); }
	}
}