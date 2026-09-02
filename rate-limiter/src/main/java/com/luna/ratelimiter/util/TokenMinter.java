package com.luna.ratelimiter.util;

import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

public class TokenMinter {
    private static final Path PRIVATE_KEY_PATH = Path.of("keys", "private.pem");

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: TokenMinter <userId> <tier>");
            System.err.println("       TokenMinter alice PREMIUM");
            System.exit(1);
        }
        String userId = args[0];
        String tier = args[1].toUpperCase();

        PrivateKey privateKey = loadPrivateKey();

        String token = Jwts.builder()
            .subject(userId)
            .claims(Map.of("tier", tier))
            .issuedAt(Date.from(Instant.now()))
            .expiration(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
            .signWith(privateKey, Jwts.SIG.RS256)
            .compact();

        System.out.println(token);
    }

    private static PrivateKey loadPrivateKey() throws Exception {
        String pem = Files.readString(PRIVATE_KEY_PATH, StandardCharsets.UTF_8);
        String base64 = pem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replaceAll("\\s+", "");
        byte[] der = Base64.getDecoder().decode(base64);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(der);
        return KeyFactory.getInstance("RSA").generatePrivate(spec);
    }
}
