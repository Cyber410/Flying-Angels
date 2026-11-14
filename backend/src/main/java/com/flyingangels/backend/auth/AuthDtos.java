package com.flyingangels.backend.auth;

import jakarta.validation.constraints.*;
import java.util.List;

public final class AuthDtos {
    private AuthDtos() {}
    public record LoginRequest(@NotBlank @Email @Size(max = 254) String email, @NotBlank @Size(max = 1000) String password) {
        @Override public String toString() { return "LoginRequest[REDACTED]"; }
    }
    public record CreateUserRequest(@NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 100) String name, @Size(min = 12, max = 72) String password,
            @NotNull StaffRole role, @Positive Long athleteId) {
        public CreateUserRequest(String email, String name, String password, StaffRole role) { this(email, name, password, role, null); }
        @Override public String toString() { return "CreateUserRequest[REDACTED]"; }
    }
    public record UpdateUserRequest(@NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 100) String name, @NotNull StaffRole role, @NotNull Boolean active,
            @Size(max = 72) String password, @Positive Long athleteId) {
        public UpdateUserRequest(String email, String name, StaffRole role, Boolean active, String password) {
            this(email, name, role, active, password, null);
        }
        @Override public String toString() { return "UpdateUserRequest[REDACTED]"; }
    }
    public record UserResponse(Long id, String email, String name, StaffRole role, boolean active, Long athleteId, String accountStatus) {
        public UserResponse(Long id, String email, String name, StaffRole role, boolean active) {
            this(id, email, name, role, active, null, active ? "ACTIVE" : "INACTIVE");
        }
        public static UserResponse from(StaffUser user) {
            return new UserResponse(user.getId(), user.getEmail(), user.getName(), user.getRole(), user.isActive(), user.getAthleteId(), user.accountStatus());
        }
    }
    public record SessionResponse(boolean authenticationRequired, UserResponse user, List<String> permissions) {}
}
