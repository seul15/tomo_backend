package com.tomo.common.auth.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tomo.common.exception.BusinessException;
import com.tomo.common.exception.ErrorCode;
import io.jsonwebtoken.Jwts;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AccessTokenProviderTest {
  private static final Instant NOW = Instant.parse("2026-11-06T00:00:00Z");

  private SecretKey signingKey;
  private AccessTokenProvider codec;

  @BeforeEach
  void setUp() {
    signingKey = Jwts.SIG.HS256.key().build();
    codec = new AccessTokenProvider(signingKey, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  @DisplayName("서명된 Access Token에서 사용자와 로그인 세션 ID를 읽는다")
  void roundTrip() {
    // given
    String token = codec.issue("user-uuid", 17L, NOW.plusSeconds(600));

    // when
    AccessTokenClaims claims = codec.verify(token);

    // then
    assertThat(claims.userId()).isEqualTo("user-uuid");
    assertThat(claims.loginSessionId()).isEqualTo(17L);
  }

  @Test
  @DisplayName("서명이 일치하지 않는 Access Token을 거부한다")
  void rejectsDifferentSigningKey() {
    // given
    String token = codec.issue("user-uuid", 17L, NOW.plusSeconds(600));
    AccessTokenProvider other =
        new AccessTokenProvider(Jwts.SIG.HS256.key().build(), Clock.fixed(NOW, ZoneOffset.UTC));

    // then
    assertThatThrownBy(() -> other.verify(token))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_ACCESS_TOKEN));
  }

  @Test
  @DisplayName("만료된 Access Token을 거부한다")
  void rejectsExpiredToken() {
    // given
    String token = codec.issue("user-uuid", 17L, NOW.plusSeconds(60));
    AccessTokenProvider later =
        new AccessTokenProvider(signingKey, Clock.fixed(NOW.plusSeconds(61), ZoneOffset.UTC));

    // then
    assertThatThrownBy(() -> later.verify(token))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_ACCESS_TOKEN));
  }

  @Test
  @DisplayName("Refresh 타입으로 표시된 토큰은 Access 인증에 사용할 수 없다")
  void rejectsWrongTokenType() {
    // given
    String otherType =
        Jwts.builder()
            .subject("user-uuid")
            .claim("sid", 17L)
            .claim("token_type", "REFRESH")
            .issuedAt(Date.from(NOW))
            .expiration(Date.from(NOW.plusSeconds(600)))
            .signWith(signingKey, Jwts.SIG.HS256)
            .compact();

    // then
    assertThatThrownBy(() -> codec.verify(otherType))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_ACCESS_TOKEN));
  }

  @Test
  @DisplayName("세션 ID가 없는 토큰은 서명이 유효해도 거부한다")
  void rejectsMissingSessionId() {
    // given
    String missingSid =
        Jwts.builder()
            .subject("user-uuid")
            .claim("token_type", "ACCESS")
            .issuedAt(Date.from(NOW))
            .expiration(Date.from(NOW.plusSeconds(600)))
            .signWith(signingKey, Jwts.SIG.HS256)
            .compact();

    // then
    assertThatThrownBy(() -> codec.verify(missingSid))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_ACCESS_TOKEN));
  }

  @Test
  @DisplayName("서명 키가 256비트보다 짧으면 코덱 생성을 거부한다")
  void rejectsShortSigningKey() {
    // given
    SecretKeySpec shortKey = new SecretKeySpec(new byte[16], "HmacSHA256");

    // then
    assertThatThrownBy(() -> new AccessTokenProvider(shortKey, Clock.fixed(NOW, ZoneOffset.UTC)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
