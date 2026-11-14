package com.flyingangels.backend.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static com.flyingangels.backend.auth.AuthDtos.*;

@Service @RequiredArgsConstructor
public class StaffUserService {
    private final StaffUserRepository users;
    private final PasswordEncoder passwords;
    private final com.flyingangels.backend.athlete.AthleteRepository athletes;
    private final com.flyingangels.backend.auth.mail.InvitationMailOutbox outbox;

    @Transactional(readOnly = true)
    public List<UserResponse> list() { return users.findAllByOrderByNameAscIdAsc().stream().map(UserResponse::from).toList(); }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        if (request.role() == StaffRole.ATHLETE) throw new IllegalArgumentException("Create Athlete accounts through the invitation endpoint and link an athlete record");
        if (request.athleteId() != null) throw new IllegalArgumentException("Only Athlete accounts can link an athlete record");
        users.lockAccounts();
        ensureUnique(request.email(), null);
        return UserResponse.from(users.saveAndFlush(new StaffUser(request.email(), request.name(), hash(request.password()), request.role())));
    }

    @Transactional
    public UserResponse update(Long id, UpdateUserRequest request, Long actorId) {
        var accounts = users.lockAccounts();
        var user = accounts.stream().filter(u -> u.getId().equals(id)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Staff account not found"));
        boolean removesAdmin = user.isActive() && user.getRole() == StaffRole.ADMIN
                && (!request.active() || request.role() != StaffRole.ADMIN);
        if (removesAdmin && (id.equals(actorId) || accounts.stream().filter(u -> u.isActive() && u.getRole() == StaffRole.ADMIN).count() <= 1))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Keep your own account and at least one Admin active");
        ensureUnique(request.email(), id);
        Long athleteId = request.role() == StaffRole.ATHLETE
                ? request.athleteId() == null ? user.getAthleteId() : request.athleteId() : null;
        validateAthleteLink(request.role(), athleteId, id);
        if (request.role() != StaffRole.ATHLETE && request.athleteId() != null) throw new IllegalArgumentException("Only Athlete accounts can link an athlete record");
        if (!user.isPasswordSet() && (request.active() || request.password() != null && !request.password().isBlank()))
            throw new IllegalArgumentException("The invited user must set their own password before the account can be activated");
        String passwordHash = request.password() == null || request.password().isBlank() ? null : hash(request.password());
        if (request.role() == StaffRole.ATHLETE && passwordHash != null)
            throw new IllegalArgumentException("Athlete passwords must be set using an invitation");
        long previousSecurityVersion = user.getSecurityVersion();
        user.linkAthlete(athleteId);
        user.update(request.email(), request.name(), request.role(), request.active(), passwordHash);
        if (!request.active()) user.cancelInvitation();
        if (user.getSecurityVersion() != previousSecurityVersion) outbox.cancelForUser(id);
        return UserResponse.from(users.saveAndFlush(user));
    }

    void ensureUnique(String email, Long id) {
        if (users.findByEmail(StaffUser.normalizeEmail(email)).filter(u -> !u.getId().equals(id)).isPresent())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists");
    }
    void validateAthleteLink(StaffRole role, Long athleteId, Long userId) {
        if (role != StaffRole.ATHLETE) {
            if (athleteId != null) throw new IllegalArgumentException("Only Athlete accounts can link an athlete record");
            return;
        }
        if (athleteId == null) throw new IllegalArgumentException("Select an athlete record for this account");
        if (!athletes.existsById(athleteId)) throw new com.flyingangels.backend.athlete.AthleteService.AthleteNotFoundException(athleteId);
        if (users.findByAthleteId(athleteId).filter(u -> !u.getId().equals(userId)).isPresent())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This athlete already has a login account");
    }
    String hash(String password) {
        if (password == null || password.length() < 12 || password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new IllegalArgumentException("Password must have at least 12 characters and at most 72 UTF-8 bytes");
        return passwords.encode(password);
    }
}
