package com.flyingangels.backend.availability;

import com.flyingangels.backend.auth.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import static com.flyingangels.backend.availability.AvailabilityDtos.*;

@RestController @RequiredArgsConstructor
public class PortalAvailabilityController {
    private final AvailabilityService availability;
    private final AthleteSessionService sessions;
    @GetMapping("/api/portal/availability")
    public List<RequestView> upcoming(@AuthenticationPrincipal Object identity) {
        var principal = sessions.require(identity); return availability.upcomingForAthlete(principal.athleteId());
    }
    @GetMapping("/api/portal/availability/{id}")
    public RequestView get(@PathVariable Long id, @AuthenticationPrincipal Object identity) {
        var principal = sessions.require(identity); return availability.get(id, principal.athleteId());
    }
    @PutMapping("/api/portal/availability/{id}/response")
    public RequestView respond(@PathVariable Long id, @AuthenticationPrincipal Object identity,
                               @Valid @RequestBody PortalReply body) {
        var principal = sessions.require(identity); return availability.replyFromPortal(id, principal, body);
    }
}
