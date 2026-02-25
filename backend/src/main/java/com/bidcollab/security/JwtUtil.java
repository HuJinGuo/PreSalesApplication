package com.bidcollab.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtUtil {
  private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);
  private final SecretKey key;
  private final long expireMinutes;

  public JwtUtil(@Value("${app.security.jwt.secret}") String secret,
      @Value("${app.security.jwt.expire-minutes}") long expireMinutes) {
    this.key = buildKey(secret);
    this.expireMinutes = expireMinutes;
  }

  private SecretKey buildKey(String secret) {
    String safeSecret = secret == null ? "" : secret.trim();
    byte[] raw = safeSecret.getBytes(StandardCharsets.UTF_8);
    if (raw.length >= 32) {
      return Keys.hmacShaKeyFor(raw);
    }
    try {
      // 对短密钥做一次 SHA-256 派生，避免启动期因密钥长度不足直接失败
      byte[] derived = MessageDigest.getInstance("SHA-256").digest(raw);
      log.warn("JWT secret length is {}, derived SHA-256 key is used. Please configure >= 32 bytes secret.",
          raw.length);
      return Keys.hmacShaKeyFor(derived);
    } catch (Exception ex) {
      throw new IllegalStateException("Failed to initialize JWT signing key", ex);
    }
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
