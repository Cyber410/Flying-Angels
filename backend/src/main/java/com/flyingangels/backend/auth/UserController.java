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
    private final AccountEnrollmentService enrollment;
    private final AccountDeletionService deletion;
    @DeleteMapping("/api/users/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, @AuthenticationPrincipal StaffPrincipal principal) {
        deletion.delete(id, principal == null ? null : principal.id());
    }
    @GetMapping("/api/users") public List<UserResponse> list() { return users.list(); }
    @PostMapping("/api/users") @ResponseStatus(HttpStatus.CREATED)
    public AccountEnrollmentService.Summary create(@Valid @RequestBody CreateUserRequest request,@AuthenticationPrincipal StaffPrincipal principal) {
        if (request.password() != null)
            throw new IllegalArgumentException("Create accounts by invitation without assigning a password; the recipient sets their own password");
        return enrollment.invite(new InvitationService.InviteRequest(request.email(), request.name(), request.role(), request.athleteId()),principal==null?null:principal.id());
    }
    @PatchMapping("/api/users/{id}")
    public UserResponse update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request,
            @AuthenticationPrincipal StaffPrincipal principal) { return users.update(id, request, principal == null ? null : principal.id()); }
}
