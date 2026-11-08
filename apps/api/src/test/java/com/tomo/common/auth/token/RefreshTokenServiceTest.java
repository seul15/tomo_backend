package com.tomo.common.auth.token;

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
import com.tomo.user.service.LoginSessionService;
import com.tomo.user.service.RefreshTokenService;
import com.tomo.user.service.model.RefreshTokenValidationResult;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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
  void issuesHashedToken() {
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

    // when & then
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

    // when & then
    assertThatThrownBy(() -> service.issue(USER_ID, SESSION_ID))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_CONFLICT));
  }

  @Test
  @DisplayName("본인 로그인 세션의 Refresh Token을 폐기한다")
  void revokesToken() {
    // given
    when(loginSessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
    when(session.getUserId()).thenReturn(USER_ID);

    // when
    service.revoke(USER_ID, SESSION_ID);

    // then
    verify(redisStringClient).delete(KEY);
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

    // when & then
    assertThatThrownBy(() -> service.issue(USER_ID, SESSION_ID))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

    verifyNoInteractions(redisStringClient);
  }

  @Test
  @DisplayName("다른 사용자 세션의 Refresh Token을 폐기할 수 없다")
  void rejectsOtherUsersRevoke() {
    // given
    when(loginSessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
    when(session.getUserId()).thenReturn("other-user");

    // when & then
    assertThatThrownBy(() -> service.revoke(USER_ID, SESSION_ID))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

    verifyNoInteractions(redisStringClient);
  }
}
