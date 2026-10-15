package com.flyingangels.backend.athlete;

import com.flyingangels.backend.athlete.dto.AthleteRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AthleteService {

	private final AthleteRepository repository;

	@Transactional(readOnly = true)
	public List<Athlete> search(String search, String gender, String team) {
		return repository.search(normalize(search), normalize(gender), normalize(team).toUpperCase());
	}

	@Transactional(readOnly = true)
	public Athlete get(Long id) {
		return repository.findById(id)
				.orElseThrow(() -> new AthleteNotFoundException(id));
	}

	@Transactional
	public Athlete save(Athlete athlete) {
		return repository.save(athlete);
	}

	@Transactional
	public Athlete create(AthleteRequest request) {
		ensureNoDuplicate(request, null);
		Athlete athlete = new Athlete(request.firstName().trim(), request.lastName().trim(), request.note(),
				request.flyStatus(), request.flyaStatus(), request.dateOfBirth(), request.gender());
		athlete.updateFrom(request);
		return repository.save(athlete);
	}

	@Transactional
	public Athlete update(Long id, AthleteRequest request) {
		Athlete athlete = get(id);
		ensureNoDuplicate(request, id);
		athlete.updateFrom(request);
		return repository.save(athlete);
	}

	@Transactional
	public void delete(Long id) {
		Athlete athlete = get(id);
		repository.delete(athlete);
	}

	private void ensureNoDuplicate(AthleteRequest request, Long excludedId) {
		if (request.dateOfBirth() == null) {
			return;
		}
		List<Athlete> matches = repository.findByFirstNameIgnoreCaseAndLastNameIgnoreCaseAndDateOfBirthAndGenderIgnoreCase(
				request.firstName().trim(), request.lastName().trim(), request.dateOfBirth(), request.gender());
		if (matches.stream().anyMatch(athlete -> !athlete.getId().equals(excludedId))) {
			throw new DuplicateAthleteException(request.firstName(), request.lastName());
		}
	}

	@Transactional
	public boolean saveIfNew(Athlete athlete) {
		if (repository.existsByImportKey(athlete.getImportKey())) {
			return false;
		}
		if (athlete.getDateOfBirth() != null && !repository
				.findByFirstNameIgnoreCaseAndLastNameIgnoreCaseAndDateOfBirthAndGenderIgnoreCase(
						athlete.getFirstName(), athlete.getLastName(), athlete.getDateOfBirth(), athlete.getGender())
				.isEmpty()) {
			return false;
		}
		if (athlete.getDateOfBirth() == null && repository
				.findByFirstNameIgnoreCaseAndLastNameIgnoreCaseAndDateOfBirthIsNullAndGenderIgnoreCase(
						athlete.getFirstName(), athlete.getLastName(), athlete.getGender())
				.stream().anyMatch(existing -> sameLegacyRecord(existing, athlete))) {
			return false;
		}
		repository.save(athlete);
		return true;
	}

	@Transactional(readOnly = true)
	public Optional<Athlete> findExistingForImport(Athlete candidate) {
		if (candidate.getImportKey() != null) {
			Optional<Athlete> keyed = repository.findByImportKey(candidate.getImportKey());
			if (keyed.isPresent()) {
				return keyed;
			}
		}
		if (candidate.getDateOfBirth() != null) {
			return repository.findByFirstNameIgnoreCaseAndLastNameIgnoreCaseAndDateOfBirthAndGenderIgnoreCase(
					candidate.getFirstName(), candidate.getLastName(), candidate.getDateOfBirth(), candidate.getGender())
					.stream().findFirst();
		}
		return repository.findByFirstNameIgnoreCaseAndLastNameIgnoreCaseAndDateOfBirthIsNullAndGenderIgnoreCase(
				candidate.getFirstName(), candidate.getLastName(), candidate.getGender()).stream()
				.filter(existing -> sameLegacyRecord(existing, candidate)).findFirst();
	}

	private boolean sameLegacyRecord(Athlete existing, Athlete incoming) {
		Set<String> existingEvents = existing.getEvents().stream()
				.map(event -> event.getEventName() + "=" + event.getResult())
				.collect(Collectors.toCollection(HashSet::new));
		Set<String> incomingEvents = incoming.getEvents().stream()
				.map(event -> event.getEventName() + "=" + event.getResult())
				.collect(Collectors.toCollection(HashSet::new));
		return normalized(existing.getNote()).equals(normalized(incoming.getNote()))
				&& normalized(existing.getFlyStatus()).equals(normalized(incoming.getFlyStatus()))
				&& normalized(existing.getFlyaStatus()).equals(normalized(incoming.getFlyaStatus()))
				&& existingEvents.equals(incomingEvents);
	}

	private String normalized(String value) {
		return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase();
	}

	private String normalize(String value) {
		return value == null ? "" : value.trim();
	}

	public static class AthleteNotFoundException extends RuntimeException {
		public AthleteNotFoundException(Long id) {
			super("Athlete not found: " + id);
		}
	}

	public static class DuplicateAthleteException extends RuntimeException {
		public DuplicateAthleteException(String firstName, String lastName) {
			super("An athlete with this name, date of birth, and gender already exists: " + firstName + " " + lastName);
		}
	}
}