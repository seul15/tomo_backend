package com.tomo.user.service;

import com.tomo.common.exception.BusinessException;
import com.tomo.common.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** 로그인 세션과 Refresh Token의 폐기를 조정한다. */
@Service
public class LoginSessionFacade {
  private static final Logger LOG = LoggerFactory.getLogger(LoginSessionFacade.class);

  private final LoginSessionService loginSessionService;
  private final RefreshTokenService refreshTokenService;

  /**
   * 로그아웃 업무에 필요한 서비스를 주입한다.
   *
   * @param loginSessionService 로그인 세션 관리 서비스
   * @param refreshTokenService Refresh Token 관리 서비스
   */
  public LoginSessionFacade(
      LoginSessionService loginSessionService, RefreshTokenService refreshTokenService) {
    this.loginSessionService = loginSessionService;
    this.refreshTokenService = refreshTokenService;
  }

  /**
   * 본인 로그인 세션을 삭제하고 Refresh Token을 폐기한다.
   *
   * <p>DB 세션 삭제가 완료된 뒤 Redis 토큰을 정리한다.
   *
   * @param userId 검증된 사용자 ID
   * @param sessionId 로그인 세션 ID
   * @throws BusinessException 요청값이 올바르지 않거나 세션을 삭제할 권한이 없는 경우
   */
  public void logout(String userId, long sessionId) {
    if (userId == null || userId.isBlank() || sessionId <= 0) {
      throw new BusinessException(ErrorCode.INVALID_REQUEST);
    }

    if (!loginSessionService.invalidateOwnedSession(userId, sessionId)) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED);
    }

    try {
      refreshTokenService.revokeAfterSessionDeletion(sessionId);
    } catch (RuntimeException exception) {
      LOG.warn("로그아웃 후 Refresh Token 정리 실패. sessionId={}", sessionId, exception);
    }
  }
}
