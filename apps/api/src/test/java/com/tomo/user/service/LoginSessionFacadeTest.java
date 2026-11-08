package com.tomo.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.tomo.common.exception.BusinessException;
import com.tomo.common.exception.ErrorCode;
import com.tomo.user.entity.LoginSession;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoginSessionFacadeTest {
  private static final String USER_ID = "user-id";
  private static final long SESSION_ID = 10L;

  @Mock private LoginSessionService loginSessionService;
  @Mock private RefreshTokenService refreshTokenService;
  @Mock private LoginSession session;

  private static final LocalDateTime EXPIRES_AT = LocalDateTime.of(2026, 11, 8, 19, 0);
  private LoginSessionFacade facade;

  @BeforeEach
  void setUp() {
    facade = new LoginSessionFacade(loginSessionService, refreshTokenService);
  }

  @Test
  @DisplayName("Refresh Token 폐기 후 DB 세션을 삭제한다")
  void logsOutSession() {
    // given
    when(loginSessionService.requireOwnedSession(USER_ID, SESSION_ID)).thenReturn(session);
    when(session.getExpiresAt()).thenReturn(EXPIRES_AT);

    // when
    facade.logout(USER_ID, SESSION_ID);

    // then
    InOrder inOrder = inOrder(loginSessionService, refreshTokenService);
    inOrder.verify(loginSessionService).requireOwnedSession(USER_ID, SESSION_ID);
    inOrder.verify(refreshTokenService).revokeBeforeSessionDeletion(SESSION_ID, EXPIRES_AT);
    inOrder.verify(loginSessionService).invalidateOwnedSession(USER_ID, SESSION_ID);
  }

  @Test
  @DisplayName("Redis 폐기 실패 시 DB 세션은 삭제하지 않는다")
  void rejectsLogoutWhenRedisFails() {
    // given
    when(loginSessionService.requireOwnedSession(USER_ID, SESSION_ID)).thenReturn(session);
    when(session.getExpiresAt()).thenReturn(EXPIRES_AT);

    doThrow(new IllegalStateException("Redis unavailable"))
        .when(refreshTokenService)
        .revokeBeforeSessionDeletion(SESSION_ID, EXPIRES_AT);

    // then
    assertThatThrownBy(() -> facade.logout(USER_ID, SESSION_ID))
        .isInstanceOf(IllegalStateException.class);

    verify(loginSessionService, never()).invalidateOwnedSession(USER_ID, SESSION_ID);
  }

  @Test
  @DisplayName("세션 삭제 권한이 없으면 Refresh Token을 폐기하지 않는다")
  void rejectsUnauthorizedLogout() {
    // given
    when(loginSessionService.requireOwnedSession(USER_ID, SESSION_ID))
        .thenThrow(new BusinessException(ErrorCode.UNAUTHORIZED));

    // then
    assertThatThrownBy(() -> facade.logout(USER_ID, SESSION_ID))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

    verifyNoInteractions(refreshTokenService);
  }

  @Test
  @DisplayName("잘못된 사용자 ID로 로그아웃하면 세션과 토큰을 변경하지 않는다")
  void rejectsInvalidLogoutRequest() {
    // then
    assertThatThrownBy(() -> facade.logout("", SESSION_ID))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));

    verifyNoInteractions(loginSessionService, refreshTokenService);
  }
}
