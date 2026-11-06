package com.examgrid.backend.user;

import com.examgrid.backend.audit.AuditService;
import com.examgrid.backend.common.error.ApiException;
import com.examgrid.backend.security.CurrentUser;
import com.examgrid.backend.security.Role;
import com.examgrid.backend.user.UserDtos.CreateUserRequest;
import com.examgrid.backend.user.UserDtos.UpdateUserRequest;
import com.examgrid.backend.user.UserDtos.UserView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class UserService {

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;
    private final Clock clock;

    public UserService(AppUserRepository users, PasswordEncoder passwordEncoder, AuditService audit, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<AppUser> search(Role role, String q, Pageable pageable) {
        return users.search(role, q == null || q.isBlank() ? null : q.strip(), pageable);
    }

    @Transactional(readOnly = true)
    public AppUser get(long id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("User", id));
    }

    @Transactional
    public AppUser create(CreateUserRequest request) {
        if (users.existsByUsernameIgnoreCase(request.username())) {
            throw ApiException.conflict("username_taken", "Username " + request.username() + " is already taken.");
        }
        validateLink(request.role(), request.studentRoll(), request.facultyCode());
        AppUser user = new AppUser(request.username().strip(), passwordEncoder.encode(request.password()),
                request.displayName().strip(), request.role(), blankToNull(request.studentRoll()),
                blankToNull(request.facultyCode()), clock.instant());
        user.assignRole(request.role(), blankToNull(request.studentRoll()), blankToNull(request.facultyCode()));
        users.save(user);
        audit.record("user.created", "user", user.getId(), Map.of("username", user.getUsername(),
                "role", user.getRole().name()));
        return user;
    }

    @Transactional
    public AppUser update(long id, UpdateUserRequest request) {
        AppUser user = get(id);
        CurrentUser me = CurrentUser.require();
        Map<String, Object> changes = new LinkedHashMap<>();
        if (request.displayName() != null && !request.displayName().equals(user.getDisplayName())) {
            changes.put("displayName", Map.of("before", user.getDisplayName(), "after", request.displayName()));
            user.setDisplayName(request.displayName().strip());
        }
        if (request.role() != null || request.studentRoll() != null || request.facultyCode() != null) {
            Role role = request.role() != null ? request.role() : user.getRole();
            String roll = request.studentRoll() != null ? blankToNull(request.studentRoll()) : user.getStudentRoll();
            String faculty = request.facultyCode() != null ? blankToNull(request.facultyCode()) : user.getFacultyCode();
            validateLink(role, roll, faculty);
            if (me.id() == id && role != Role.EXAM_CELL) {
                throw ApiException.conflict("cannot_demote_self", "You cannot remove your own exam cell role.");
            }
            if (user.getRole() == Role.EXAM_CELL && role != Role.EXAM_CELL) {
                ensureAnotherAdmin(user);
            }
            if (role != user.getRole()) {
                changes.put("role", Map.of("before", user.getRole().name(), "after", role.name()));
            }
            user.assignRole(role, roll, faculty);
        }
        if (request.enabled() != null && request.enabled() != user.isEnabled()) {
            if (!request.enabled() && me.id() == id) {
                throw ApiException.conflict("cannot_disable_self", "You cannot disable your own account.");
            }
            if (!request.enabled() && user.getRole() == Role.EXAM_CELL) {
                ensureAnotherAdmin(user);
            }
            changes.put("enabled", Map.of("before", user.isEnabled(), "after", request.enabled()));
            user.setEnabled(request.enabled());
        }
        if (request.password() != null) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
            changes.put("password", "reset");
        }
        if (!changes.isEmpty()) {
            audit.record("user.updated", "user", user.getId(), changes);
        }
        return user;
    }

    @Transactional
    public void changeOwnPassword(String currentPassword, String newPassword) {
        AppUser user = get(CurrentUser.require().id());
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw ApiException.badRequest("wrong_password", "The current password is incorrect.");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        audit.record("user.password_changed", "user", user.getId(), Map.of());
    }

    public UserView view(AppUser user) {
        return UserView.of(user);
    }

    private void ensureAnotherAdmin(AppUser user) {
        if (user.isEnabled() && users.countByRoleAndEnabledTrue(Role.EXAM_CELL) <= 1) {
            throw ApiException.conflict("last_admin", "At least one active exam cell account must remain.");
        }
    }

    private static void validateLink(Role role, String roll, String faculty) {
        if (role == Role.STUDENT && (roll == null || roll.isBlank())) {
            throw ApiException.badRequest("student_roll_required", "Student accounts need a roll number.");
        }
        if (role == Role.FACULTY && (faculty == null || faculty.isBlank())) {
            throw ApiException.badRequest("faculty_code_required", "Faculty accounts need a staff code.");
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
