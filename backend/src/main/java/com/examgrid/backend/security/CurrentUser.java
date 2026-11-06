package com.examgrid.backend.security;

import com.examgrid.backend.common.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Optional;

/**
 * The authenticated caller, read from the verified JWT. This is the only way controllers learn
 * who a student or faculty member is.
 */
public record CurrentUser(long id, String username, Role role, String studentRoll, String facultyCode) {

    /** The caller, or empty for anonymous requests. */
    public static Optional<CurrentUser> find() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken token) {
            Jwt jwt = token.getToken();
            Object uid = jwt.getClaim(TokenService.CLAIM_USER_ID);
            return Optional.of(new CurrentUser(
                    uid instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(uid)),
                    jwt.getSubject(),
                    Role.valueOf(jwt.getClaimAsString(TokenService.CLAIM_ROLE)),
                    jwt.getClaimAsString(TokenService.CLAIM_ROLL),
                    jwt.getClaimAsString(TokenService.CLAIM_FACULTY)));
        }
        return Optional.empty();
    }

    public static CurrentUser require() {
        return find().orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "unauthenticated",
                "Authentication is required."));
    }

    /** Name recorded in the audit log: the username, or {@code system} for background work. */
    public static String actorName() {
        return find().map(CurrentUser::username).orElse("system");
    }

    public String requireStudentRoll() {
        if (role != Role.STUDENT || studentRoll == null) {
            throw ApiException.forbidden("not_a_student", "Only students have a personal timetable.");
        }
        return studentRoll;
    }

    public String requireFacultyCode() {
        if (role != Role.FACULTY || facultyCode == null) {
            throw ApiException.forbidden("not_faculty", "Only faculty have invigilation duties.");
        }
        return facultyCode;
    }
}
