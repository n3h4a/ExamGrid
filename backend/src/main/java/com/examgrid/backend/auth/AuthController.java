package com.examgrid.backend.auth;

import com.examgrid.backend.security.CurrentUser;
import com.examgrid.backend.user.UserDtos.ChangePasswordRequest;
import com.examgrid.backend.user.UserDtos.UserView;
import com.examgrid.backend.user.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication")
public class AuthController {

    private final AuthService auth;
    private final UserService users;

    public AuthController(AuthService auth, UserService users) {
        this.auth = auth;
        this.users = users;
    }

    public record LoginRequest(@NotBlank @Size(max = 64) String username, @NotBlank @Size(max = 128) String password) {
    }

    public record TokenResponse(String accessToken, String tokenType, Instant expiresAt, UserView user) {
    }

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "Exchange username and password for a bearer token")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        AuthService.LoginResult result = auth.login(request.username(), request.password());
        return new TokenResponse(result.token().value(), "Bearer", result.token().expiresAt(),
                UserView.of(result.user()));
    }

    @GetMapping("/me")
    @Operation(summary = "The authenticated user")
    public UserView me() {
        return UserView.of(users.get(CurrentUser.require().id()));
    }

    @PostMapping("/password")
    @Operation(summary = "Change your own password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        users.changeOwnPassword(request.currentPassword(), request.newPassword());
        return ResponseEntity.noContent().build();
    }
}
