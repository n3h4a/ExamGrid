package com.examgrid.backend.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenServiceTest {

    private static final String SECRET = "unit-test-secret-0123456789abcdef-xyz";

    @Test
    void issuedTokensRoundTripWithIdentityClaims() {
        TokenService tokens = service(SECRET, Clock.systemUTC(), Duration.ofHours(1));
        TokenService.IssuedToken issued = tokens.issue(7, "21cs045", Role.STUDENT, "21CS045", null);
        Jwt jwt = tokens.decoder().decode(issued.value());
        assertThat(jwt.getSubject()).isEqualTo("21cs045");
        assertThat(jwt.getClaimAsString(TokenService.CLAIM_ROLE)).isEqualTo("STUDENT");
        assertThat(jwt.getClaimAsString(TokenService.CLAIM_ROLL)).isEqualTo("21CS045");
        assertThat(jwt.hasClaim(TokenService.CLAIM_FACULTY)).isFalse();
        assertThat(jwt.getExpiresAt()).isEqualTo(issued.expiresAt());
    }

    @Test
    void tokensSignedWithAnotherSecretAreRejected() {
        String token = service("another-secret-0123456789abcdef-abc", Clock.systemUTC(), Duration.ofHours(1))
                .issue(1, "admin", Role.EXAM_CELL, null, null).value();
        assertThatThrownBy(() -> service(SECRET, Clock.systemUTC(), Duration.ofHours(1)).decoder().decode(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void expiredTokensAreRejected() {
        Clock past = Clock.fixed(Instant.now().minus(Duration.ofHours(3)), ZoneOffset.UTC);
        String token = service(SECRET, past, Duration.ofHours(1)).issue(1, "admin", Role.EXAM_CELL, null, null).value();
        assertThatThrownBy(() -> service(SECRET, Clock.systemUTC(), Duration.ofHours(1)).decoder().decode(token))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("expired");
    }

    private static TokenService service(String secret, Clock clock, Duration ttl) {
        return new TokenService(new SecurityProperties(secret, "examgrid", ttl, List.of()), clock);
    }
}
