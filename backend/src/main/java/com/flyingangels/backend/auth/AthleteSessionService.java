package com.flyingangels.backend.auth;

import com.flyingangels.backend.athlete.AthleteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.Objects;

/** Resolve ownership exclusively from a revalidated, activated Athlete account. */
@Service @RequiredArgsConstructor
public class AthleteSessionService {
    private final StaffUserRepository users;
    private final AthleteRepository athletes;
    public StaffPrincipal require(Object principal) {
        if (principal instanceof StaffPrincipal account && account.role() == StaffRole.ATHLETE) {
            var user = users.findById(account.id()).filter(u -> u.isActive() && u.getRole() == StaffRole.ATHLETE
                    && u.getSecurityVersion() == account.securityVersion() && u.getAthleteId() != null
                    && Objects.equals(u.getAthleteId(), account.athleteId()) && athletes.existsById(u.getAthleteId()));
            if (user.isPresent()) return account;
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Athlete access unavailable");
    }
}
