package com.gymms.security;

import com.gymms.entity.AppUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Issues signed (HS256) JWT access tokens. Verification is done by Spring Security's resource server. */
@Service
public class JwtService {

    private final JwtEncoder encoder;
    private final Clock clock;
    private final long expiryHours;

    public JwtService(JwtEncoder encoder, Clock clock, @Value("${app.jwt.expiry-hours}") long expiryHours) {
        this.encoder = encoder;
        this.clock = clock;
        this.expiryHours = expiryHours;
    }

    public String issueToken(AppUser user) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("gym-membership-management-system")
                .issuedAt(now)
                .expiresAt(now.plus(expiryHours, ChronoUnit.HOURS))
                .subject(user.getUsername())
                .claim("roles", List.of(user.getRole().name()))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
