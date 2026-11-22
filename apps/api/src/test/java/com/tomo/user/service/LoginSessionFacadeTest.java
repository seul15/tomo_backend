package com.tomo.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.tomo.common.auth.token.AccessTokenProvider;
import com.tomo.common.exception.BusinessException;
import com.tomo.common.exception.CommonErrorCode;
import com.tomo.user.entity.LoginSession;
import com.tomo.user.exception.UserErrorCode;
import com.tomo.user.service.model.TokenRefreshResult;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

@ExtendWith(MockitoExtension.class)
class LoginSessionFacadeTest {
  private static final String USER_ID = "user-id";
  private static final long SESSION_ID = 10L;
  private static final Instant NOW = Instant.parse("2026-11-07T00:00:00Z");
  private static final Duration ACCESS_TTL = Duration.ofHours(1);
  private static final Duration REFRESH_TTL = Duration.ofDays(7);
  private static final LocalDateTime EXPIRES_AT =
      LocalDateTime.ofInstant(NOW.plus(REFRESH_TTL), ZoneOffset.UTC);
  private static final String OLD_REFRESH = "10." + "A".repeat(43);
  private static final String NEW_REFRESH = "10." + "B".repeat(43);

  @Mock private LoginSessionService loginSessionService;
  @Mock private RefreshTokenService refreshTokenService;
  @Mock private AccessTokenProvider accessTokenProvider;
  @Mock private PlatformTransactionManager transactionManager;
  @Mock private LoginSession session;

  private LoginSessionFacade facade;

  @BeforeEach
  void setUp() {
    lenient()
        .when(transactionManager.getTransaction(any(TransactionDefinition.class)))
        .thenAnswer(invocation -> new SimpleTransactionStatus());

    facade =
        new LoginSessionFacade(
            loginSessionService,
            refreshTokenService,
            accessTokenProvider,
            Clock.fixed(NOW, ZoneOffset.UTC),
            transactionManager,
            ACCESS_TTL,
            REFRESH_TTL);
  }

  @Test
  @DisplayName("현재 Refresh Token 교체 시 DB 만료시간을 7일 연장하고 새 토큰 쌍을 발급한다")
  void refreshesCurrentToken() {
    // given
    when(refreshTokenService.extractSessionId(OLD_REFRESH)).thenReturn(SESSION_ID);
    when(loginSessionService.requireRefreshSessionForUpdate(SESSION_ID)).thenReturn(session);
    when(session.getUserId()).thenReturn(USER_ID);
    when(loginSessionService.isActiveUser(USER_ID)).thenReturn(true);
    when(refreshTokenService.rotate(OLD_REFRESH, SESSION_ID, REFRESH_TTL, true))
        .thenReturn(
            new RefreshTokenService.Rotation(
                RefreshTokenService.RotationStatus.ROTATED, NEW_REFRESH));
    when(accessTokenProvider.issue(USER_ID, SESSION_ID, NOW.plus(ACCESS_TTL)))
        .thenReturn("new-access-jwt");

    // when
    TokenRefreshResult result = facade.refresh(OLD_REFRESH);

    // then
    assertThat(result.accessToken()).isEqualTo("new-access-jwt");
    assertThat(result.refreshToken()).isEqualTo(NEW_REFRESH);
    assertThat(result.accessTokenExpiresIn()).isEqualTo(3600L);
    assertThat(result.refreshTokenExpiresIn()).isEqualTo(604800L);
    verify(loginSessionService).extendExpiration(SESSION_ID, EXPIRES_AT);
    verify(transactionManager).commit(any(TransactionStatus.class));
  }

  @Test
  @DisplayName("10초 이내 직전 Refresh Token 재사용은 거부하지만 세션은 폐기하지 않는다")
  void rejectsPreviousTokenWithinGraceWithoutDeletingSession() {
    // given
    when(refreshTokenService.extractSessionId(OLD_REFRESH)).thenReturn(SESSION_ID);
    when(loginSessionService.requireRefreshSessionForUpdate(SESSION_ID)).thenReturn(session);
    when(session.getUserId()).thenReturn(USER_ID);
    when(loginSessionService.isActiveUser(USER_ID)).thenReturn(true);
    when(refreshTokenService.rotate(OLD_REFRESH, SESSION_ID, REFRESH_TTL, true))
        .thenReturn(
            new RefreshTokenService.Rotation(
                RefreshTokenService.RotationStatus.PREVIOUS_WITHIN_GRACE, null));

    // when
    // then
    assertThatThrownBy(() -> facade.refresh(OLD_REFRESH))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(UserErrorCode.INVALID_REFRESH_TOKEN));
    verify(transactionManager).commit(any(TransactionStatus.class));
    verify(loginSessionService, never()).invalidateOwnedSession(USER_ID, SESSION_ID);
    verifyNoInteractions(accessTokenProvider);
  }

  @Test
  @DisplayName("10초 이후 직전 Refresh Token 재사용은 DB 커밋 후 401을 반환한다")
  void revokesSessionAfterPreviousTokenReplay() {
    // given
    when(refreshTokenService.extractSessionId(OLD_REFRESH)).thenReturn(SESSION_ID);
    when(loginSessionService.requireRefreshSessionForUpdate(SESSION_ID)).thenReturn(session);
    when(session.getUserId()).thenReturn(USER_ID);
    when(session.getExpiresAt()).thenReturn(EXPIRES_AT);
    when(loginSessionService.isActiveUser(USER_ID)).thenReturn(true);
    when(refreshTokenService.rotate(OLD_REFRESH, SESSION_ID, REFRESH_TTL, true))
        .thenReturn(
            new RefreshTokenService.Rotation(
                RefreshTokenService.RotationStatus.PREVIOUS_AFTER_GRACE, null));
    when(loginSessionService.invalidateOwnedSession(USER_ID, SESSION_ID)).thenReturn(true);

    // when
    // then
    assertThatThrownBy(() -> facade.refresh(OLD_REFRESH))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(UserErrorCode.INVALID_REFRESH_TOKEN));
    InOrder order = inOrder(refreshTokenService, loginSessionService, transactionManager);
    order.verify(refreshTokenService).revokeBeforeSessionDeletion(SESSION_ID, EXPIRES_AT);
    order.verify(loginSessionService).invalidateOwnedSession(USER_ID, SESSION_ID);
    order.verify(transactionManager).commit(any(TransactionStatus.class));
    verify(transactionManager, never()).rollback(any(TransactionStatus.class));
  }

  @Test
  @DisplayName("비활성 계정의 유효한 Refresh Token은 403을 반환하고 세션을 유지한다")
  void rejectsInactiveAccount() {
    // given
    when(refreshTokenService.extractSessionId(OLD_REFRESH)).thenReturn(SESSION_ID);
    when(loginSessionService.requireRefreshSessionForUpdate(SESSION_ID)).thenReturn(session);
    when(session.getUserId()).thenReturn(USER_ID);
    when(loginSessionService.isActiveUser(USER_ID)).thenReturn(false);
    when(refreshTokenService.rotate(OLD_REFRESH, SESSION_ID, REFRESH_TTL, false))
        .thenReturn(
            new RefreshTokenService.Rotation(RefreshTokenService.RotationStatus.FORBIDDEN, null));

    // when
    // then
    assertThatThrownBy(() -> facade.refresh(OLD_REFRESH))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(UserErrorCode.TOKEN_REFRESH_FORBIDDEN));
    verify(loginSessionService, never()).invalidateOwnedSession(USER_ID, SESSION_ID);
  }

  @Test
  @DisplayName("DB 커밋 실패 시 새 Refresh Token을 현재 값과 비교하여 조건부 폐기한다")
  void compensatesAfterCommitFailure() {
    // given
    when(refreshTokenService.extractSessionId(OLD_REFRESH)).thenReturn(SESSION_ID);
    when(loginSessionService.requireRefreshSessionForUpdate(SESSION_ID)).thenReturn(session);
    when(session.getUserId()).thenReturn(USER_ID);
    when(loginSessionService.isActiveUser(USER_ID)).thenReturn(true);
    when(refreshTokenService.rotate(OLD_REFRESH, SESSION_ID, REFRESH_TTL, true))
        .thenReturn(
            new RefreshTokenService.Rotation(
                RefreshTokenService.RotationStatus.ROTATED, NEW_REFRESH));
    doThrow(new IllegalStateException("DB commit failed"))
        .when(transactionManager)
        .commit(any(TransactionStatus.class));

    // when
    // then
    assertThatThrownBy(() -> facade.refresh(OLD_REFRESH))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("DB commit failed");
    verify(refreshTokenService).revokeIfCurrentMatches(SESSION_ID, NEW_REFRESH, REFRESH_TTL);
    verifyNoInteractions(accessTokenProvider);
  }

  @Test
  @DisplayName("Refresh Token 폐기 후 DB 세션을 삭제한다")
  void logsOutSession() {
    // given
    when(loginSessionService.requireOwnedSession(USER_ID, SESSION_ID)).thenReturn(session);
    when(session.getExpiresAt()).thenReturn(EXPIRES_AT);
    when(loginSessionService.invalidateOwnedSession(USER_ID, SESSION_ID)).thenReturn(true);

    // when
    facade.logout(USER_ID, SESSION_ID);

    // then
    InOrder order = inOrder(loginSessionService, refreshTokenService, transactionManager);
    order.verify(loginSessionService).requireOwnedSession(USER_ID, SESSION_ID);
    order.verify(refreshTokenService).revokeBeforeSessionDeletion(SESSION_ID, EXPIRES_AT);
    order.verify(loginSessionService).invalidateOwnedSession(USER_ID, SESSION_ID);
    order.verify(transactionManager).commit(any(TransactionStatus.class));
  }

  @Test
  @DisplayName("Redis 폐기 실패 시 DB 세션을 삭제하지 않는다")
  void rejectsLogoutWhenRedisFails() {
    // given
    when(loginSessionService.requireOwnedSession(USER_ID, SESSION_ID)).thenReturn(session);
    when(session.getExpiresAt()).thenReturn(EXPIRES_AT);
    doThrow(new IllegalStateException("Redis unavailable"))
        .when(refreshTokenService)
        .revokeBeforeSessionDeletion(SESSION_ID, EXPIRES_AT);

    // when
    // then
    assertThatThrownBy(() -> facade.logout(USER_ID, SESSION_ID))
        .isInstanceOf(IllegalStateException.class);
    verify(loginSessionService, never()).invalidateOwnedSession(USER_ID, SESSION_ID);
    verify(transactionManager).rollback(any(TransactionStatus.class));
  }

  @Test
  @DisplayName("소유권 확인에 실패하면 Refresh Token을 폐기하지 않는다")
  void rejectsUnauthorizedLogout() {
    // given
    when(loginSessionService.requireOwnedSession(USER_ID, SESSION_ID))
        .thenThrow(new BusinessException(CommonErrorCode.UNAUTHORIZED));

    // when
    // then
    assertThatThrownBy(() -> facade.logout(USER_ID, SESSION_ID))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(CommonErrorCode.UNAUTHORIZED));
    verifyNoInteractions(refreshTokenService);
  }

  @Test
  @DisplayName("잘못된 사용자 ID로 로그아웃하면 Redis를 변경하지 않는다")
  void rejectsInvalidLogoutRequest() {
    // given
    when(loginSessionService.requireOwnedSession("", SESSION_ID))
        .thenThrow(new BusinessException(CommonErrorCode.INVALID_REQUEST));

    // when
    // then
    assertThatThrownBy(() -> facade.logout("", SESSION_ID))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_REQUEST));
    verify(loginSessionService).requireOwnedSession("", SESSION_ID);
    verifyNoInteractions(refreshTokenService);
  }
}
