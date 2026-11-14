package com.flyingangels.backend.athlete;

import com.flyingangels.backend.athlete.dto.AthleteProfileResponse;
import com.flyingangels.backend.athlete.dto.AthleteRequest;
import com.flyingangels.backend.athlete.dto.AthleteSummaryResponse;
import com.flyingangels.backend.athlete.dto.AthleteArchiveRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class AthleteController {

	private final AthleteService athleteService;

	@GetMapping("/api/athletes")
	public List<AthleteSummaryResponse> search(@RequestParam(defaultValue = "") String search,
			@RequestParam(defaultValue = "") String gender,
			@RequestParam(defaultValue = "") String team,
            @RequestParam(defaultValue="active") String archive,
            @RequestParam(required=false) Integer minAge, @RequestParam(required=false) Integer maxAge,
            @RequestParam(required=false) @org.springframework.format.annotation.DateTimeFormat(iso=org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate ageAsOf) {
        var athletes = minAge == null && maxAge == null
                ? (archive.equals("active") ? athleteService.search(search,gender,team)
                    : athleteService.searchIncludingArchived(search,gender,team))
                : athleteService.search(search,gender,team,minAge,maxAge,ageAsOf);
		if (!List.of("active","archived","all").contains(archive)) throw new IllegalArgumentException("Archive filter must be active, archived, or all");
        return athletes.stream().filter(a -> archive.equals("all") || a.isArchived() == archive.equals("archived"))
            .map(AthleteSummaryResponse::from).toList();
	}

    @PutMapping("/api/athletes/archive")
    public List<AthleteSummaryResponse> archive(@Valid @RequestBody AthleteArchiveRequest request) {
        return athleteService.setArchived(request.athleteIds(),request.archived()).stream().map(AthleteSummaryResponse::from).toList();
    }
	@GetMapping("/api/athletes/{id}")
	public AthleteProfileResponse profile(@PathVariable Long id) {
		return athleteService.profile(id);
	}

	@PostMapping("/api/athletes")
	@ResponseStatus(HttpStatus.CREATED)
	public AthleteProfileResponse create(@Valid @RequestBody AthleteRequest request) {
		return athleteService.createProfile(request);
	}

	@PutMapping("/api/athletes/{id}")
	public AthleteProfileResponse update(@PathVariable Long id, @Valid @RequestBody AthleteRequest request) {
		return athleteService.updateProfile(id, request);
	}

	@DeleteMapping("/api/athletes/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable Long id) {
		athleteService.delete(id);
	}

}
