package com.flyingangels.backend.athlete;

import com.flyingangels.backend.auth.AthleteSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor
public class AgeGroupController {
    private final AgeGroupService groups;
    private final AthleteSessionService sessions;
    @GetMapping("/api/age-groups") public AgeGroupService.Configuration staff() { return groups.configuration(); }
    @GetMapping("/api/portal/age-groups") public AgeGroupService.Configuration portal(@AuthenticationPrincipal Object identity) {
        sessions.require(identity); return groups.configuration();
    }
}
