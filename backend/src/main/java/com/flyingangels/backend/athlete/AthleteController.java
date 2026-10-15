package com.flyingangels.backend.athlete;

import com.flyingangels.backend.athlete.dto.AthleteProfileResponse;
import com.flyingangels.backend.athlete.dto.AthleteRequest;
import com.flyingangels.backend.athlete.dto.AthleteSummaryResponse;
import com.flyingangels.backend.athlete.dto.AthleteImportResponse;
import com.flyingangels.backend.event.EventResultRepository;
import com.flyingangels.backend.event.EventService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import org.springframework.web.bind.annotation.CrossOrigin;
 
@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class AthleteController {

	private final AthleteService athleteService;
	private final AthleteImportService importService;
	private final EventResultRepository eventResultRepository;
	private final EventService eventService;

	@GetMapping("/api/athletes")
	public List<AthleteSummaryResponse> search(@RequestParam(defaultValue = "") String search,
			@RequestParam(defaultValue = "") String gender,
			@RequestParam(defaultValue = "") String team) {
		return athleteService.search(search, gender, team).stream().map(AthleteSummaryResponse::from).toList();
	}

	@GetMapping("/api/athletes/{id}")
	public AthleteProfileResponse profile(@PathVariable Long id) {
		Athlete athlete = athleteService.get(id);
		return AthleteProfileResponse.from(athlete, eventResultRepository.findByAthleteId(id));
	}

	@PostMapping("/api/athletes")
	@ResponseStatus(HttpStatus.CREATED)
	public AthleteProfileResponse create(@Valid @RequestBody AthleteRequest request) {
		return AthleteProfileResponse.from(athleteService.create(request), List.of());
	}

	@PutMapping("/api/athletes/{id}")
	public AthleteProfileResponse update(@PathVariable Long id, @Valid @RequestBody AthleteRequest request) {
		return AthleteProfileResponse.from(athleteService.update(id, request), List.of());
	}

	@DeleteMapping("/api/athletes/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable Long id) {
		athleteService.delete(id);
	}

	@PostMapping(value = "/api/athletes/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public AthleteImportResponse importAthletes(@RequestParam("file") MultipartFile file) throws IOException {
		AthleteImportService.ImportSummary summary = importService.importFile(file);
		int importedCount = 0;
		int importedEventCount = 0;
		for (Athlete athlete : summary.athletes()) {
			boolean imported = athleteService.saveIfNew(athlete);
			Athlete persisted = imported ? athlete : athleteService.findExistingForImport(athlete).orElse(null);
			if (imported) {
				importedCount++;
				importedEventCount += athlete.getEvents().size();
			}
			if (persisted != null) {
				eventService.recordImportedResults(persisted);
			}
		}
		return new AthleteImportResponse(importedCount, importedEventCount, summary.athleteCount() - importedCount);
	}
}