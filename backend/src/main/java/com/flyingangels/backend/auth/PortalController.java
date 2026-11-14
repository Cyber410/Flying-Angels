package com.flyingangels.backend.auth;

import com.flyingangels.backend.athlete.AthleteService;
import com.flyingangels.backend.event.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController @RequiredArgsConstructor
public class PortalController {
    private final AthleteSessionService sessions;
    private final AthleteService athletes;
    private final com.flyingangels.backend.availability.AvailabilityService availability;
    public record PortalAssignment(String meetName, String eventName, Long meetId, Long eventId) {}
    public record PortalResult(String meetName, String eventName, String result, BigDecimal score, ResultUnit unit, ResultStatus status, String resultLabel) {}
    public record PortalProfile(Long id, String firstName, String lastName, LocalDate dateOfBirth, String gender,
                                List<PortalAssignment> assignments, List<PortalResult> results,
                                List<com.flyingangels.backend.availability.AvailabilityDtos.RequestView> availabilityRequests) {}

    @GetMapping("/api/portal/profile")
    public PortalProfile profile(@AuthenticationPrincipal Object identity) {
        var principal = sessions.require(identity);
        var profile = athletes.profile(principal.athleteId());
        return new PortalProfile(profile.id(), profile.firstName(), profile.lastName(), profile.dateOfBirth(), profile.gender(),
                profile.participations().stream().map(p -> new PortalAssignment(p.meetName(), p.eventName(), p.meetId(), p.eventId())).toList(),
                profile.events().stream().map(r -> new PortalResult(r.meetName(), r.categoryName(), r.result(), r.score(), r.unit(), r.status(), r.resultLabel())).toList(),
                availability.upcomingForAthlete(principal.athleteId()));
    }
}
