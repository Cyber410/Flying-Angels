package com.flyingangels.backend.athlete;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.time.LocalDate;

public interface AthleteRepository extends JpaRepository<Athlete, Long> {

	@EntityGraph(attributePaths = "events")
	@Query("""
		SELECT a FROM Athlete a
		WHERE (:search = '' OR LOWER(CONCAT(a.firstName, ' ', a.lastName)) LIKE LOWER(CONCAT('%', :search, '%')))
		AND (:gender = '' OR LOWER(a.gender) = LOWER(:gender))
		AND (:team = '' OR (:team = 'FLY' AND a.flyStatus IS NOT NULL AND a.flyStatus <> '')
			OR (:team = 'FLYA' AND a.flyaStatus IS NOT NULL AND a.flyaStatus <> ''))
		ORDER BY LOWER(a.lastName), LOWER(a.firstName)
		""")
	List<Athlete> search(@Param("search") String search, @Param("gender") String gender, @Param("team") String team);

	@EntityGraph(attributePaths = "events")
	Optional<Athlete> findById(Long id);

	boolean existsByImportKey(String importKey);

	Optional<Athlete> findByImportKey(String importKey);

	List<Athlete> findByFirstNameIgnoreCaseAndLastNameIgnoreCaseAndDateOfBirthAndGenderIgnoreCase(
			String firstName, String lastName, LocalDate dateOfBirth, String gender);

	@EntityGraph(attributePaths = "events")
	List<Athlete> findByFirstNameIgnoreCaseAndLastNameIgnoreCaseAndDateOfBirthIsNullAndGenderIgnoreCase(
			String firstName, String lastName, String gender);
}