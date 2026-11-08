package com.tomo.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.tomo.common.exception.BusinessException;
import com.tomo.common.exception.ErrorCode;
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

  private LoginSessionFacade facade;

  @BeforeEach
  void setUp() {
    facade = new LoginSessionFacade(loginSessionService, refreshTokenService);
  }

  @Test
  @DisplayName("로그아웃하면 DB 세션 삭제 후 Refresh Token을 폐기한다")
  void logsOutSession() {
    // given
    when(loginSessionService.invalidateOwnedSession(USER_ID, SESSION_ID)).thenReturn(true);

    // when
    facade.logout(USER_ID, SESSION_ID);

    // then
    InOrder inOrder = inOrder(loginSessionService, refreshTokenService);

    inOrder.verify(loginSessionService).invalidateOwnedSession(USER_ID, SESSION_ID);

    inOrder.verify(refreshTokenService).revokeAfterSessionDeletion(SESSION_ID);
  }

  @Test
  @DisplayName("세션 삭제 권한이 없으면 Refresh Token을 폐기하지 않는다")
  void rejectsUnauthorizedLogout() {
    // given
    when(loginSessionService.invalidateOwnedSession(USER_ID, SESSION_ID)).thenReturn(false);

    // when & then
    assertThatThrownBy(() -> facade.logout(USER_ID, SESSION_ID))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

    verifyNoInteractions(refreshTokenService);
  }

  @Test
  @DisplayName("잘못된 사용자 ID로 로그아웃하면 세션과 토큰을 변경하지 않는다")
  void rejectsInvalidLogoutRequest() {
    // when & then
    assertThatThrownBy(() -> facade.logout("", SESSION_ID))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));

    verifyNoInteractions(loginSessionService, refreshTokenService);
  }
}
