package com.tomo.user.service;

import com.tomo.common.exception.BusinessException;
import com.tomo.common.exception.CommonErrorCode;
import com.tomo.user.entity.LoginSession;
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
   * Refresh Token 폐기 후 본인 로그인 세션을 삭제한다.
   *
   * @param userId 검증된 사용자 ID
   * @param sessionId 로그인 세션 ID
   * @throws BusinessException 요청 또는 세션 소유권이 유효하지 않은 경우
   */
  public void logout(String userId, long sessionId) {
    if (userId == null || userId.isBlank() || sessionId <= 0) {
      throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
    }

    LoginSession session = loginSessionService.requireOwnedSession(userId, sessionId);

    refreshTokenService.revokeBeforeSessionDeletion(sessionId, session.getExpiresAt());

    boolean deleted = loginSessionService.invalidateOwnedSession(userId, sessionId);

    if (!deleted) {
      LOG.warn("로그아웃 중 DB 세션 삭제 대상 없음. sessionId={}", sessionId);
      return;
    }

    LOG.info("로그아웃 완료. sessionId={}", sessionId);
  }
}
