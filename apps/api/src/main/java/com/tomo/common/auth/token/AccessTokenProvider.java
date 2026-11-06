package com.tomo.common.auth.token;

import com.tomo.common.exception.BusinessException;
import com.tomo.common.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/** 로그인 세션 ID를 포함하는 서명된 Access JWT의 생성·검증만 담당한다. */
public final class AccessTokenProvider {
  private static final String SESSION_ID = "sid";
  private static final String TOKEN_TYPE = "token_type";
  private static final String ACCESS = "ACCESS";
  private static final String HMAC_SHA_256 = "HmacSHA256";
  private static final int MINIMUM_KEY_BYTES = 32;

  private final SecretKey signingKey;
  private final Clock clock;

  /**
   * 코드 외부에서 주입된 서명 키와 시간을 사용한다.
   *
   * @param signingKey 최소 256비트 HMAC 서명 키
   * @param clock 검증·발급 기준 시각
   * @throws IllegalArgumentException 서명 키가 256비트보다 짧거나 인코딩 불가능한 경우
   */
  public AccessTokenProvider(SecretKey signingKey, Clock clock) {
    Objects.requireNonNull(signingKey);
    this.clock = Objects.requireNonNull(clock);
    byte[] keyBytes = signingKey.getEncoded();
    if (keyBytes == null || keyBytes.length < MINIMUM_KEY_BYTES) {
      throw new IllegalArgumentException("JWT 서명 키는 최소 256비트여야 합니다.");
    }
    // 키의 외부 변경이 코덱에 영향을 주지 않도록 복사하고 HS256 알고리즘을 고정한다.
    this.signingKey = new SecretKeySpec(keyBytes, HMAC_SHA_256);
  }

  /**
   * 지정된 만료 시각으로 Access Token을 생성한다. 만료 기간 정책은 호출 측에서 결정한다.
   *
   * @param userId 로그인 사용자 ID
   * @param sessionId login_sessions ID
   * @param expiresAt Access Token 만료 시각
   * @return 서명된 JWT 문자열
   * @throws IllegalArgumentException 사용자·세션 ID가 유효하지 않거나 만료 시각이 현재 이전인 경우
   */
  public String issue(String userId, long sessionId, Instant expiresAt) {
    Instant now = clock.instant();
    if (userId == null || userId.isBlank() || sessionId <= 0) {
      throw new IllegalArgumentException("사용자 및 로그인 세션 ID가 필요합니다.");
    }
    if (expiresAt == null || !expiresAt.isAfter(now)) {
      throw new IllegalArgumentException("토큰 만료 시각은 현재 이후여야 합니다.");
    }
    return Jwts.builder()
        .subject(userId)
        .claim(SESSION_ID, sessionId)
        .claim(TOKEN_TYPE, ACCESS)
        .issuedAt(Date.from(now))
        .expiration(Date.from(expiresAt))
        .signWith(signingKey, Jwts.SIG.HS256)
        .compact();
  }

  /**
   * 서명, 만료 시각과 필수 클레임을 검증하고 사용자·세션 식별값을 반환한다.
   *
   * @param token Bearer 접두사 없는 Access Token 문자열
   * @return 검증한 사용자 및 세션 ID
   * @throws BusinessException 토큰이 유효하지 않은 경우
   */
  public AccessTokenClaims verify(String token) {
    if (token == null || token.isBlank()) {
      throw new BusinessException(ErrorCode.INVALID_ACCESS_TOKEN);
    }
    try {
      Jws<Claims> parsed =
          Jwts.parser()
              .verifyWith(signingKey)
              .clock(() -> Date.from(clock.instant()))
              .build()
              .parseSignedClaims(token);
      Claims claims = parsed.getPayload();
      Object sid = claims.get(SESSION_ID);
      String subject = claims.getSubject();
      if (!"HS256".equals(parsed.getHeader().getAlgorithm())
          || !ACCESS.equals(claims.get(TOKEN_TYPE))
          || subject == null
          || subject.isBlank()
          || (!(sid instanceof Long) && !(sid instanceof Integer))) {
        throw new BusinessException(ErrorCode.INVALID_ACCESS_TOKEN);
      }
      long sessionId = ((Number) sid).longValue();
      if (sessionId <= 0) {
        throw new BusinessException(ErrorCode.INVALID_ACCESS_TOKEN);
      }
      if (claims.getExpiration() == null
          || claims.getIssuedAt() == null
          || claims.getIssuedAt().after(Date.from(clock.instant()))) {
        throw new BusinessException(ErrorCode.INVALID_ACCESS_TOKEN);
      }
      return new AccessTokenClaims(subject, sessionId);
    } catch (JwtException | IllegalArgumentException e) {
      throw new BusinessException(ErrorCode.INVALID_ACCESS_TOKEN);
    }
  }
}
