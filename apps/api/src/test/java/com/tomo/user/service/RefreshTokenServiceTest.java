package com.tomo.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.tomo.common.exception.BusinessException;
import com.tomo.common.exception.ErrorCode;
import com.tomo.integration.redis.RedisStringClient;
import com.tomo.user.entity.LoginSession;
import com.tomo.user.repository.LoginSessionRepository;
import com.tomo.user.service.model.RefreshTokenValidationResult;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {
  private static final String USER_ID = "user-id";
  private static final long SESSION_ID = 10L;
  private static final String KEY = "auth:refresh:session:10";

  private static final Instant NOW = Instant.parse("2026-11-07T00:00:00Z");

  @Mock private LoginSessionRepository loginSessionRepository;
  @Mock private LoginSessionService loginSessionService;
  @Mock private RedisStringClient redisStringClient;
  @Mock private LoginSession session;

  private RefreshTokenService service;

  @BeforeEach
  void setUp() {
    service =
        new RefreshTokenService(
            loginSessionRepository,
            loginSessionService,
            redisStringClient,
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  @DisplayName("Refresh Token을 발급하면 원문이 아닌 해시를 저장한다")
  void issuesHashedToken() throws Exception {
    // given
    prepareValidSession();

    when(redisStringClient.setIfAbsent(eq(KEY), anyString(), eq(Duration.ofMinutes(30))))
        .thenReturn(true);

    // when
    String token = service.issue(USER_ID, SESSION_ID);

    // then
    ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);

    verify(redisStringClient).setIfAbsent(eq(KEY), hash.capture(), eq(Duration.ofMinutes(30)));

    assertThat(token).startsWith("10.");
    assertThat(hash.getValue()).matches("[0-9a-f]{64}");
    assertThat(hash.getValue()).isNotEqualTo(token);

    byte[] digest =
        MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));

    String expectedHash = HexFormat.of().formatHex(digest);

    assertThat(hash.getValue()).isEqualTo(expectedHash);
  }

  @Test
  @DisplayName("유효한 Refresh Token을 새로운 토큰으로 교체한다")
  void rotatesValidRefreshToken() throws Exception {
    // given
    prepareValidSession();

    String oldToken = "10." + "A".repeat(43);

    String oldHash =
        HexFormat.of()
            .formatHex(
                MessageDigest.getInstance("SHA-256")
                    .digest(oldToken.getBytes(StandardCharsets.UTF_8)));

    when(redisStringClient.compareAndSetWithTtl(
            eq(KEY), eq(oldHash), anyString(), eq(Duration.ofMinutes(30))))
        .thenReturn(true);

    // when
    String rotated = service.rotate(oldToken);

    // then
    ArgumentCaptor<String> newHash = ArgumentCaptor.forClass(String.class);

    verify(redisStringClient)
        .compareAndSetWithTtl(eq(KEY), eq(oldHash), newHash.capture(), eq(Duration.ofMinutes(30)));

    String expectedHash =
        HexFormat.of()
            .formatHex(
                MessageDigest.getInstance("SHA-256")
                    .digest(rotated.getBytes(StandardCharsets.UTF_8)));

    assertThat(rotated).startsWith("10.").isNotEqualTo(oldToken);
    assertThat(newHash.getValue()).isEqualTo(expectedHash);
  }

  @Test
  @DisplayName("기존 토큰 해시가 일치하지 않으면 갱신을 거부한다")
  void rejectsConcurrentRotation() {
    // given
    prepareValidSession();

    String oldToken = "10." + "A".repeat(43);

    when(redisStringClient.compareAndSetWithTtl(
            eq(KEY), anyString(), anyString(), eq(Duration.ofMinutes(30))))
        .thenReturn(false);

    // then
    assertThatThrownBy(() -> service.rotate(oldToken))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_ACCESS_TOKEN));
  }

  @Test
  @DisplayName("로그아웃 중 발급된 Refresh Token은 정리한다")
  void rejectsTokenIssuedAfterLogout() {
    // given
    prepareValidSession();

    when(loginSessionService.isValidAccessSession(USER_ID, SESSION_ID)).thenReturn(true, false);

    when(redisStringClient.setIfAbsent(eq(KEY), anyString(), eq(Duration.ofMinutes(30))))
        .thenReturn(true);

    // then
    assertThatThrownBy(() -> service.issue(USER_ID, SESSION_ID))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

    verify(redisStringClient).deleteIfMatches(eq(KEY), anyString());
  }

  @Test
  @DisplayName("유효한 Refresh Token은 사용자와 로그인 세션을 반환한다")
  void findsValidSession() {
    // given
    prepareValidSession();

    when(redisStringClient.setIfAbsent(eq(KEY), anyString(), eq(Duration.ofMinutes(30))))
        .thenReturn(true);

    String token = service.issue(USER_ID, SESSION_ID);

    ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);

    verify(redisStringClient).setIfAbsent(eq(KEY), hash.capture(), eq(Duration.ofMinutes(30)));

    when(redisStringClient.get(KEY)).thenReturn(hash.getValue());

    // when
    Optional<RefreshTokenValidationResult> result = service.findValidSession(token);

    // then
    assertThat(result).contains(new RefreshTokenValidationResult(USER_ID, SESSION_ID));
  }

  @Test
  @DisplayName("저장된 해시와 일치하지 않는 Refresh Token은 거부한다")
  void rejectsInvalidToken() {
    // given
    String token = "10." + "A".repeat(43);

    when(redisStringClient.get(KEY)).thenReturn("0".repeat(64));

    // when
    Optional<RefreshTokenValidationResult> result = service.findValidSession(token);

    // then
    assertThat(result).isEmpty();
    verifyNoInteractions(loginSessionRepository);
  }

  @Test
  @DisplayName("세션 ID가 같아도 다른 Refresh Token은 검증에 실패한다")
  void rejectsDifferentTokenForSameSession() {
    // given
    prepareValidSession();

    when(redisStringClient.setIfAbsent(eq(KEY), anyString(), eq(Duration.ofMinutes(30))))
        .thenReturn(true);

    String token = service.issue(USER_ID, SESSION_ID);

    ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);

    verify(redisStringClient).setIfAbsent(eq(KEY), hash.capture(), eq(Duration.ofMinutes(30)));

    when(redisStringClient.get(KEY)).thenReturn(hash.getValue());

    String differentToken =
        token.substring(0, token.length() - 1) + (token.endsWith("A") ? "B" : "A");

    // when
    Optional<RefreshTokenValidationResult> result = service.findValidSession(differentToken);

    // then
    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("형식이 잘못된 Refresh Token은 Redis 조회 없이 거부한다")
  void rejectsMalformedToken() {
    // when
    Optional<RefreshTokenValidationResult> result = service.findValidSession("invalid-token");

    // then
    assertThat(result).isEmpty();
    verifyNoInteractions(redisStringClient);
  }

  @Test
  @DisplayName("만료된 로그인 세션에는 Refresh Token을 발급하지 않는다")
  void rejectsExpiredSession() {
    // given
    when(loginSessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
    when(session.getUserId()).thenReturn(USER_ID);
    when(session.getExpiresAt())
        .thenReturn(LocalDateTime.ofInstant(NOW.minusSeconds(1), ZoneOffset.UTC));

    // then
    assertThatThrownBy(() -> service.issue(USER_ID, SESSION_ID))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

    verifyNoInteractions(redisStringClient);
  }

  @Test
  @DisplayName("같은 세션에 Refresh Token이 이미 존재하면 재발급하지 않는다")
  void rejectsDuplicateIssue() {
    // given
    prepareValidSession();

    when(redisStringClient.setIfAbsent(eq(KEY), anyString(), eq(Duration.ofMinutes(30))))
        .thenReturn(false);

    // then
    assertThatThrownBy(() -> service.issue(USER_ID, SESSION_ID))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_CONFLICT));
  }

  private void prepareValidSession() {
    when(loginSessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));

    when(session.getExpiresAt())
        .thenReturn(LocalDateTime.ofInstant(NOW.plusSeconds(1800), ZoneOffset.UTC));

    when(session.getUserId()).thenReturn(USER_ID);

    when(loginSessionService.isValidAccessSession(USER_ID, SESSION_ID)).thenReturn(true);
  }

  @Test
  @DisplayName("다른 사용자의 로그인 세션으로 Refresh Token을 발급할 수 없다")
  void rejectsOtherUsersSession() {
    // given
    when(loginSessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
    when(session.getUserId()).thenReturn("other-user");

    // then
    assertThatThrownBy(() -> service.issue(USER_ID, SESSION_ID))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

    verifyNoInteractions(redisStringClient);
  }

  @Test
  @DisplayName("DB 세션 삭제 이후 Redis Refresh Token을 폐기한다")
  void revokesTokenAfterSessionDeletion() {
    // when
    service.revokeAfterSessionDeletion(SESSION_ID);

    // then
    verify(redisStringClient).delete(KEY);
    verifyNoInteractions(loginSessionRepository);
  }
}
