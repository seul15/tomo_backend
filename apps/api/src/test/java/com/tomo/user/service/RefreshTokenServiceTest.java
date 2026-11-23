package com.tomo.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.tomo.common.auth.exception.AuthErrorCode;
import com.tomo.common.exception.BusinessException;
import com.tomo.integration.redis.RedisStringClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

  private static final long SESSION_ID = 10L;
  private static final String KEY = "auth:refresh:session:10";

  private static final Instant NOW = Instant.parse("2026-11-07T00:00:00Z");

  private static final Duration REFRESH_TTL = Duration.ofDays(7);

  private static final Duration REUSE_GRACE = Duration.ofSeconds(10);

  @Mock private RedisStringClient redisStringClient;

  private RefreshTokenService service;

  @BeforeEach
  void setUp() {
    service = new RefreshTokenService(redisStringClient, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  @DisplayName("Refresh Token을 최초 발급하면 원문이 아닌 SHA-256 해시를 저장한다")
  void issuesHashedToken() {
    // given
    LocalDateTime expiresAt =
        LocalDateTime.ofInstant(NOW.plus(Duration.ofMinutes(30)), ZoneOffset.UTC);

    when(redisStringClient.setIfAbsent(eq(KEY), anyString(), eq(Duration.ofMinutes(30))))
        .thenReturn(true);

    // when
    String token = service.issue(SESSION_ID, expiresAt);

    // then
    ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);

    verify(redisStringClient)
        .setIfAbsent(eq(KEY), hashCaptor.capture(), eq(Duration.ofMinutes(30)));

    assertThat(token).startsWith("10.");
    assertThat(token).matches("10\\.[A-Za-z0-9_-]{43}");
    assertThat(hashCaptor.getValue())
        .matches("[0-9a-f]{64}")
        .isEqualTo(hash(token))
        .isNotEqualTo(token);
  }

  @Test
  @DisplayName("유효한 현재 Refresh Token을 새 토큰으로 교체한다")
  void rotatesValidRefreshToken() {
    // given
    String currentToken = "10." + "A".repeat(43);

    when(redisStringClient.rotateWithHistory(
            eq(KEY),
            eq(hash(currentToken)),
            anyString(),
            eq(REFRESH_TTL),
            eq(REUSE_GRACE),
            eq(true)))
        .thenReturn(1L);

    // when
    RefreshTokenService.Rotation result =
        service.rotate(currentToken, SESSION_ID, REFRESH_TTL, true);

    // then
    ArgumentCaptor<String> newHashCaptor = ArgumentCaptor.forClass(String.class);

    verify(redisStringClient)
        .rotateWithHistory(
            eq(KEY),
            eq(hash(currentToken)),
            newHashCaptor.capture(),
            eq(REFRESH_TTL),
            eq(REUSE_GRACE),
            eq(true));

    assertThat(result.status()).isEqualTo(RefreshTokenService.RotationStatus.ROTATED);

    assertThat(result.newToken()).startsWith("10.").isNotEqualTo(currentToken);

    assertThat(newHashCaptor.getValue()).isEqualTo(hash(result.newToken()));
  }

  @ParameterizedTest
  @CsvSource({"0, INVALID", "2, PREVIOUS_WITHIN_GRACE", "3, PREVIOUS_AFTER_GRACE", "4, FORBIDDEN"})
  @DisplayName("Redis의 교체 결과에 따라 Refresh Token 상태를 정확하게 구분한다")
  void distinguishesRotationResults(
      long redisResult, RefreshTokenService.RotationStatus expectedStatus) {

    // given
    String currentToken = "10." + "A".repeat(43);

    when(redisStringClient.rotateWithHistory(
            eq(KEY),
            eq(hash(currentToken)),
            anyString(),
            eq(REFRESH_TTL),
            eq(REUSE_GRACE),
            eq(true)))
        .thenReturn(redisResult);

    // when
    RefreshTokenService.Rotation result =
        service.rotate(currentToken, SESSION_ID, REFRESH_TTL, true);

    // then
    assertThat(result.status()).isEqualTo(expectedStatus);
    assertThat(result.newToken()).isNull();
  }

  @Test
  @DisplayName("저장된 해시와 다른 Refresh Token은 교체하지 않는다")
  void rejectsInvalidToken() {
    // given
    String token = "10." + "B".repeat(43);

    when(redisStringClient.rotateWithHistory(
            eq(KEY), eq(hash(token)), anyString(), eq(REFRESH_TTL), eq(REUSE_GRACE), eq(true)))
        .thenReturn(0L);

    // when
    RefreshTokenService.Rotation result = service.rotate(token, SESSION_ID, REFRESH_TTL, true);

    // then
    assertThat(result.status()).isEqualTo(RefreshTokenService.RotationStatus.INVALID);

    assertThat(result.newToken()).isNull();
  }

  @Test
  @DisplayName("같은 세션 ID라도 토큰 원문이 다르면 검증에 실패한다")
  void rejectsDifferentTokenForSameSession() {
    // given
    String differentToken = "10." + "C".repeat(43);

    when(redisStringClient.rotateWithHistory(
            eq(KEY),
            eq(hash(differentToken)),
            anyString(),
            eq(REFRESH_TTL),
            eq(REUSE_GRACE),
            eq(true)))
        .thenReturn(0L);

    // when
    RefreshTokenService.Rotation result =
        service.rotate(differentToken, SESSION_ID, REFRESH_TTL, true);

    // then
    assertThat(result.status()).isEqualTo(RefreshTokenService.RotationStatus.INVALID);

    assertThat(result.newToken()).isNull();
  }

  @Test
  @DisplayName("형식이 잘못된 Refresh Token은 Redis 호출 없이 거부한다")
  void rejectsMalformedToken() {
    // when
    // then
    assertThatThrownBy(() -> service.rotate("invalid-token", SESSION_ID, REFRESH_TTL, true))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN));

    verifyNoInteractions(redisStringClient);
  }

  @Test
  @DisplayName("Refresh Token의 세션 ID가 요청된 세션과 다르면 거부한다")
  void rejectsDifferentSessionId() {
    // given
    String token = "10." + "A".repeat(43);

    // when
    // then
    assertThatThrownBy(() -> service.rotate(token, 20L, REFRESH_TTL, true))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN));

    verifyNoInteractions(redisStringClient);
  }

  @Test
  @DisplayName("만료된 로그인 세션에는 Refresh Token을 발급하지 않는다")
  void rejectsExpiredSession() {
    // given
    LocalDateTime expiresAt = LocalDateTime.ofInstant(NOW.minusSeconds(1), ZoneOffset.UTC);

    // when
    // then
    assertThatThrownBy(() -> service.issue(SESSION_ID, expiresAt))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN));

    verifyNoInteractions(redisStringClient);
  }

  @Test
  @DisplayName("같은 세션에 이미 Refresh Token이 존재하면 중복 발급하지 않는다")
  void rejectsDuplicateIssue() {
    // given
    LocalDateTime expiresAt = LocalDateTime.ofInstant(NOW.plusSeconds(1800), ZoneOffset.UTC);

    when(redisStringClient.setIfAbsent(eq(KEY), anyString(), eq(Duration.ofMinutes(30))))
        .thenReturn(false);

    // when
    // then
    assertThatThrownBy(() -> service.issue(SESSION_ID, expiresAt))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN));
  }

  @Test
  @DisplayName("로그아웃 시 Redis Refresh Token에 폐기 표시를 저장한다")
  void revokesTokenBeforeSessionDeletion() {
    // given
    LocalDateTime expiresAt = LocalDateTime.ofInstant(NOW.plusSeconds(1800), ZoneOffset.UTC);

    // when
    service.revokeBeforeSessionDeletion(SESSION_ID, expiresAt);

    // then
    verify(redisStringClient).set(KEY, "__REVOKED__", Duration.ofMinutes(30));
  }

  @Test
  @DisplayName("DB 실패 보상 시 현재 토큰이 일치하는 경우에만 폐기한다")
  void revokesOnlyMatchingCurrentToken() {
    // given
    String newToken = "10." + "D".repeat(43);

    when(redisStringClient.revokeIfCurrentMatches(KEY, hash(newToken), "__REVOKED__", REFRESH_TTL))
        .thenReturn(true);

    // when
    boolean revoked = service.revokeIfCurrentMatches(SESSION_ID, newToken, REFRESH_TTL);

    // then
    assertThat(revoked).isTrue();

    verify(redisStringClient)
        .revokeIfCurrentMatches(KEY, hash(newToken), "__REVOKED__", REFRESH_TTL);
  }

  @Test
  @DisplayName("유효한 Refresh Token에서 세션 ID를 추출한다")
  void extractsSessionId() {
    // given
    String token = "10." + "A".repeat(43);

    // when
    long sessionId = service.extractSessionId(token);

    // then
    assertThat(sessionId).isEqualTo(SESSION_ID);
  }

  private static String hash(String token) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));

      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", exception);
    }
  }
}
