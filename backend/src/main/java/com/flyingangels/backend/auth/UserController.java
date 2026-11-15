package com.flyingangels.backend.auth;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.util.List;
import static com.flyingangels.backend.auth.AuthDtos.*;

@RestController @RequiredArgsConstructor
public class UserController {
    private final StaffUserService users;
    private final InvitationService invitations;
    @GetMapping("/api/users") public List<UserResponse> list() { return users.list(); }
    @PostMapping("/api/users") @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
        if (request.password() != null)
            throw new IllegalArgumentException("Create accounts by invitation without assigning a password; the recipient sets their own password");
        return invitations.invite(new InvitationService.InviteRequest(request.email(), request.name(), request.role(), request.athleteId()));
    }
    @PatchMapping("/api/users/{id}")
    public UserResponse update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request,
            @AuthenticationPrincipal StaffPrincipal principal) { return users.update(id, request, principal == null ? null : principal.id()); }
}
