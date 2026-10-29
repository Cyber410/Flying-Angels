package com.flyingangels.backend.athlete;

import com.flyingangels.backend.athlete.dto.AthleteRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AthleteService {

	private final AthleteRepository repository;
    private final com.flyingangels.backend.event.EventResultRepository resultRepository;
    private final com.flyingangels.backend.event.EventParticipationRepository participationRepository;
    private final com.flyingangels.backend.note.AthleteNoteRepository noteRepository;

	@Transactional(readOnly = true)
	public List<Athlete> search(String search, String gender, String team) {
		return repository.search(normalize(search), normalize(gender), normalize(team).toUpperCase());
	}

	@Transactional(readOnly = true)
	public Athlete get(Long id) {
		return repository.findById(id)
				.orElseThrow(() -> new AthleteNotFoundException(id));
	}

    @Transactional(readOnly=true)
    public com.flyingangels.backend.athlete.dto.AthleteProfileResponse profile(Long id) {
        return com.flyingangels.backend.athlete.dto.AthleteProfileResponse.from(get(id),resultRepository.findByAthleteId(id),
            participationRepository.findByAthleteId(id));
    }
    @Transactional
    public com.flyingangels.backend.athlete.dto.AthleteProfileResponse createProfile(AthleteRequest request) { return profile(create(request).getId()); }
    @Transactional
    public com.flyingangels.backend.athlete.dto.AthleteProfileResponse updateProfile(Long id,AthleteRequest request) { update(id,request); return profile(id); }
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
		noteRepository.deleteByAthleteId(id);
        resultRepository.deleteByAthleteId(id);
        participationRepository.deleteByAthleteId(id);
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
