package com.luna.ratelimiter.tier;

import com.luna.ratelimiter.config.JwtProperties;
import com.luna.ratelimiter.tier.TierResolver.ResolvedIdentity;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class JwtTierResolver implements TierResolver {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final Logger log = LoggerFactory.getLogger(JwtTierResolver.class);

    private final JwtProperties props;
    private PublicKey verificationKey;

    public JwtTierResolver(JwtProperties props) {
        this.props = props;
    }

    @PostConstruct
    void loadKey() throws IOException, Exception {
        try (InputStream in = props.getPublicKey().getInputStream()) {
            String pem = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            String base64 = pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s+", "");

            byte[] der = Base64.getDecoder().decode(base64);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(der);
            this.verificationKey = KeyFactory.getInstance("RSA").generatePublic(spec);

            log.info("RSA public key loaded for JWT verification: {} ({} bits)",
                verificationKey.getAlgorithm(),
                ((java.security.interfaces.RSAPublicKey) verificationKey)
                    .getModulus().bitLength());
        }
    }

    @Override
    public Optional<ResolvedIdentity> resolve(HttpServletRequest request) {
        String header = request.getHeader(AUTH_HEADER);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return Optional.empty();
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();

        try {
            Jws<Claims> jws = Jwts.parser()
                .verifyWith(verificationKey)
                .build()
                .parseSignedClaims(token);

            Claims claims = jws.getPayload();
            String userId = claims.getSubject();
            String tierString = claims.get(props.getTierClaim(), String.class);

            if (userId == null || userId.isBlank()) {
                log.debug("JWT missing subject (sub) claim");
                return Optional.empty();
            }
            if (tierString == null) {
                log.debug("JWT missing tier claim '{}'", props.getTierClaim());
                return Optional.empty();
            }

            Tier tier;
            try {
                tier = Tier.valueOf(tierString.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.debug("JWT contains unknown tier '{}'", tierString);
                return Optional.empty();
            }

            return Optional.of(new ResolvedIdentity(userId, tier));

        } catch (JwtException e) {
            // Catches: bad signature, expired, malformed, unsupported algorithm
            log.debug("JWT validation failed: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
