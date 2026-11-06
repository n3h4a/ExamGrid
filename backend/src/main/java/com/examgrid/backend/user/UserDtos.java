package com.examgrid.backend.user;

import com.examgrid.backend.security.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** Request and response bodies of the user API. Passwords never appear in responses. */
public final class UserDtos {

    private UserDtos() {
    }

    public record UserView(long id, String username, String displayName, Role role, String studentRoll,
                           String facultyCode, boolean enabled, Instant createdAt, Instant lastLoginAt) {

        public static UserView of(AppUser u) {
            return new UserView(u.getId(), u.getUsername(), u.getDisplayName(), u.getRole(), u.getStudentRoll(),
                    u.getFacultyCode(), u.isEnabled(), u.getCreatedAt(), u.getLastLoginAt());
        }
    }

    public record CreateUserRequest(
            @NotBlank @Size(min = 3, max = 64) @Pattern(regexp = "[A-Za-z0-9._@-]+",
                    message = "may contain letters, digits and . _ @ - only") String username,
            @NotBlank @Size(max = 120) String displayName,
            @NotBlank @Size(min = 8, max = 128) String password,
            @NotNull Role role,
            @Size(max = 32) String studentRoll,
            @Size(max = 32) String facultyCode) {
    }

    public record UpdateUserRequest(
            @Size(min = 1, max = 120) String displayName,
            Role role,
            @Size(max = 32) String studentRoll,
            @Size(max = 32) String facultyCode,
            Boolean enabled,
            @Size(min = 8, max = 128) String password) {
    }

    public record ChangePasswordRequest(@NotBlank String currentPassword,
                                        @NotBlank @Size(min = 8, max = 128) String newPassword) {
    }
}
