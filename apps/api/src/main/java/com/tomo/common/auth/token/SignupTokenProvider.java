package com.tomo.common.auth.token;

import com.tomo.common.auth.exception.AuthErrorCode;
import com.tomo.common.exception.BusinessException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/** 가입 절차 전용 JWT를 발급·검증하며 DB의 PENDING 상태는 판단하지 않는다. */
public final class SignupTokenProvider {
  private static final String TOKEN_TYPE = "token_type";
  private static final String SIGNUP = "SIGNUP";
  private static final String HMAC_SHA_256 = "HmacSHA256";

  private final SecretKey signingKey;
  private final Clock clock;
  private final Duration expiration;

  /**
   * 서명키·기준 시계·가입 토큰 만료기간을 지정한다.
   *
   * @param signingKey 최소 256비트 서명키
   * @param clock 기준 시각
   * @param expiration 가입 토큰 만료기간
   * @throws IllegalArgumentException 서명키가 짧거나 만료기간이 올바르지 않은 경우
   */
  public SignupTokenProvider(SecretKey signingKey, Clock clock, Duration expiration) {
    byte[] encoded = Objects.requireNonNull(signingKey).getEncoded();
    if (encoded == null || encoded.length < 32) {
      throw new IllegalArgumentException("JWT 서명키는 최소 256비트여야 합니다.");
    }
    if (Objects.requireNonNull(expiration).isNegative() || expiration.isZero()) {
      throw new IllegalArgumentException("가입 토큰 만료기간은 양수여야 합니다.");
    }
    this.signingKey = new SecretKeySpec(encoded, HMAC_SHA_256);
    this.clock = Objects.requireNonNull(clock);
    this.expiration = expiration;
  }

  /**
   * PENDING 가입 절차에 사용할 Signup Token을 발급한다.
   *
   * @param userId 소셜 인증을 완료한 사용자 ID
   * @return 서명된 가입 전용 JWT
   * @throws IllegalArgumentException 사용자 ID가 비어 있는 경우
   */
  public String issue(String userId) {
    if (userId == null || userId.isBlank()) {
      throw new IllegalArgumentException("사용자 ID가 필요합니다.");
    }
    Instant now = clock.instant();
    return Jwts.builder()
        .subject(userId)
        .claim(TOKEN_TYPE, SIGNUP)
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plus(expiration)))
        .signWith(signingKey, Jwts.SIG.HS256)
        .compact();
  }

  /**
   * Signup Token의 서명·만료·전용 타입을 확인한다.
   *
   * @param token 검증할 Signup JWT
   * @return 검증된 사용자 ID(PENDING 상태는 업무 서비스에서 추가 확인)
   * @throws BusinessException 만료되었거나 타입·클레임·서명이 잘못된 경우
   */
  public String verify(String token) {
    if (token == null || token.isBlank()) {
      throw new BusinessException(AuthErrorCode.INVALID_SIGNUP_TOKEN);
    }
    try {
      Jws<Claims> parsed =
          Jwts.parser()
              .verifyWith(signingKey)
              .clock(() -> Date.from(clock.instant()))
              .build()
              .parseSignedClaims(token);
      Claims claims = parsed.getPayload();
      if (!"HS256".equals(parsed.getHeader().getAlgorithm())
          || !SIGNUP.equals(claims.get(TOKEN_TYPE))
          || claims.getSubject() == null
          || claims.getSubject().isBlank()
          || claims.getIssuedAt() == null
          || claims.getExpiration() == null
          || claims.getIssuedAt().after(Date.from(clock.instant()))) {
        throw new BusinessException(AuthErrorCode.INVALID_SIGNUP_TOKEN);
      }
      return claims.getSubject();
    } catch (JwtException | IllegalArgumentException exception) {
      throw new BusinessException(AuthErrorCode.INVALID_SIGNUP_TOKEN);
    }
  }
}
