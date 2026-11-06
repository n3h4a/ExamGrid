package com.examgrid.backend.auth;

import com.examgrid.backend.audit.AuditService;
import com.examgrid.backend.common.error.ApiException;
import com.examgrid.backend.security.TokenService;
import com.examgrid.backend.user.AppUser;
import com.examgrid.backend.user.AppUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Map;
import java.util.Optional;

/**
 * Username/password login. Unknown users, wrong passwords and disabled accounts all produce
 * the same response, and unknown users still pay for a hash comparison, so the endpoint does
 * not reveal which usernames exist.
 */
@Service
public class AuthService {

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final TokenService tokens;
    private final AuditService audit;
    private final Clock clock;
    private final String dummyHash;

    public AuthService(AppUserRepository users, PasswordEncoder encoder, TokenService tokens, AuditService audit,
                       Clock clock) {
        this.users = users;
        this.encoder = encoder;
        this.tokens = tokens;
        this.audit = audit;
        this.clock = clock;
        this.dummyHash = encoder.encode("timing-equaliser-password");
    }

    /** A successful login. */
    public record LoginResult(AppUser user, TokenService.IssuedToken token) {
    }

    @Transactional(noRollbackFor = ApiException.class)
    public LoginResult login(String username, String password) {
        Optional<AppUser> found = users.findByUsernameIgnoreCase(username.strip());
        boolean ok = encoder.matches(password, found.map(AppUser::getPasswordHash).orElse(dummyHash))
                && found.isPresent() && found.get().isEnabled();
        if (!ok) {
            audit.recordIndependently(username.strip(), "auth.login_failed", "user",
                    found.map(AppUser::getId).orElse(null), Map.of());
            throw new ApiException(HttpStatus.UNAUTHORIZED, "invalid_credentials", "Invalid username or password.");
        }
        AppUser user = found.get();
        user.setLastLoginAt(clock.instant());
        audit.recordIndependently(user.getUsername(), "auth.login", "user", user.getId(), Map.of());
        TokenService.IssuedToken token = tokens.issue(user.getId(), user.getUsername(), user.getRole(),
                user.getStudentRoll(), user.getFacultyCode());
        return new LoginResult(user, token);
    }
}
