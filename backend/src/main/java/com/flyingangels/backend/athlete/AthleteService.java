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
    public List<Athlete> search(String search, String gender, String team, Integer minAge, Integer maxAge,
                                java.time.LocalDate ageAsOf) {
        var ages = new AgeRange(minAge,maxAge);
        if (!ages.active()) return search(search,gender,team);
        var dates = ages.birthDates(ageAsOf);
        String name = normalize(search).toLowerCase(java.util.Locale.ROOT);
        String sex = normalize(gender).toLowerCase(java.util.Locale.ROOT);
        String club = normalize(team).toUpperCase(java.util.Locale.ROOT);
        org.springframework.data.jpa.domain.Specification<Athlete> filter = (root,query,cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (!name.isEmpty()) predicates.add(cb.like(cb.lower(cb.concat(cb.concat(root.get("firstName")," "),root.get("lastName"))),"%"+name+"%"));
            if (!sex.isEmpty()) predicates.add(cb.equal(cb.lower(root.get("gender")),sex));
            if (!club.isEmpty()) {
                if (!club.equals("FLY") && !club.equals("FLYA")) predicates.add(cb.disjunction());
                else {
                    var status = root.<String>get(club.equals("FLY") ? "flyStatus" : "flyaStatus");
                    predicates.add(cb.and(cb.isNotNull(status),cb.notEqual(status,"")));
                }
            }
            predicates.add(cb.lessThanOrEqualTo(root.get("dateOfBirth"),dates.throughInclusive()));
            if (dates.afterExclusive() != null) predicates.add(cb.greaterThan(root.get("dateOfBirth"),dates.afterExclusive()));
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        return repository.findAll(filter,org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Order.asc("lastName").ignoreCase(),
                org.springframework.data.domain.Sort.Order.asc("firstName").ignoreCase(),
                org.springframework.data.domain.Sort.Order.asc("id")));
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
