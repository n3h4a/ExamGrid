package com.examgrid.backend.user;

import com.examgrid.backend.audit.AuditService;
import com.examgrid.backend.security.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Map;

/**
 * Creates the first exam cell account on an empty database from
 * {@code EXAMGRID_BOOTSTRAP_ADMIN_USERNAME} / {@code _PASSWORD}. It does nothing once any user
 * exists, so the variables can stay set, and nothing at all when they are absent. It runs before
 * the demo seeder, whose accounts would otherwise count as "a user exists".
 */
@Component
@Order(1)
public class BootstrapAdmin implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdmin.class);

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final Clock clock;
    private final String username;
    private final String password;

    public BootstrapAdmin(AppUserRepository users, PasswordEncoder encoder, AuditService audit, Clock clock,
                          @Value("${examgrid.bootstrap-admin.username:}") String username,
                          @Value("${examgrid.bootstrap-admin.password:}") String password) {
        this.users = users;
        this.encoder = encoder;
        this.audit = audit;
        this.clock = clock;
        this.username = username;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (username.isBlank() || password.isBlank() || users.count() > 0) {
            return;
        }
        if (password.length() < 8) {
            throw new IllegalStateException("examgrid.bootstrap-admin.password must be at least 8 characters");
        }
        AppUser admin = users.save(new AppUser(username, encoder.encode(password), "Exam cell administrator",
                Role.EXAM_CELL, null, null, clock.instant()));
        audit.record("user.bootstrapped", "user", admin.getId(), Map.of("username", username));
        log.info("Created bootstrap exam cell account '{}'", username);
    }
}
