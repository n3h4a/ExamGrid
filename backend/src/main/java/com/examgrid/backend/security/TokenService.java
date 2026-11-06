package com.examgrid.backend.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Issues and verifies HS256 access tokens. Claims: {@code sub} (username), {@code uid},
 * {@code role}, and {@code roll} or {@code faculty} for portal users. Portal endpoints read the
 * student or faculty identity from these claims only, never from the URL, which rules out
 * IDOR by construction.
 */
@Service
public class TokenService {

    public static final String CLAIM_USER_ID = "uid";
    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_ROLL = "roll";
    public static final String CLAIM_FACULTY = "faculty";

    private final SecurityProperties properties;
    private final Clock clock;
    private final JwtEncoder encoder;
    private final JwtDecoder decoder;

    public TokenService(SecurityProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        SecretKey key = new SecretKeySpec(properties.jwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        NimbusJwtDecoder nimbus = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        nimbus.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.issuer()));
        this.decoder = nimbus;
    }

    /** A signed token and its expiry. */
    public record IssuedToken(String value, Instant expiresAt) {
    }

    public IssuedToken issue(long userId, String username, Role role, String studentRoll, String facultyCode) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant expires = now.plus(properties.tokenTtl());
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(username)
                .issuedAt(now)
                .expiresAt(expires)
                .claim(CLAIM_USER_ID, userId)
                .claim(CLAIM_ROLE, role.name());
        if (studentRoll != null) {
            claims.claim(CLAIM_ROLL, studentRoll);
        }
        if (facultyCode != null) {
            claims.claim(CLAIM_FACULTY, facultyCode);
        }
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
        return new IssuedToken(token, expires);
    }

    public JwtDecoder decoder() {
        return decoder;
    }
}
