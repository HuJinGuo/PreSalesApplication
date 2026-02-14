package com.bidcollab.security;

import io.jsonwebtoken.Jwts;

import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtUtil {
  private final SecretKey key;
  private final long expireMinutes;

  public JwtUtil(@Value("${app.security.jwt.secret}") String secret,
      @Value("${app.security.jwt.expire-minutes}") long expireMinutes) {
    this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.expireMinutes = expireMinutes;
  }

  public String generateToken(String username) {
    Instant now = Instant.now();
    Instant exp = now.plusSeconds(expireMinutes * 60);
    return Jwts.builder()
        .subject(username)
        .issuedAt(Date.from(now))
        .expiration(Date.from(exp))
        .signWith(key)
        .compact();
  }

  public String parseUsername(String token) {
    return Jwts.parser()
        .verifyWith(key)
        .build()
        .parseSignedClaims(token)
        .getPayload()
        .getSubject();
  }
}
