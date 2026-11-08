package com.tomo.common.auth.session;

/** Access Token에 포함된 사용자와 로그인 세션의 유효성을 확인하는 계약. */
public interface AccessSessionValidator {

  /**
   * 사용자와 로그인 세션이 현재 인증에 유효한지 확인한다.
   *
   * @param userId 사용자 ID
   * @param sessionId 로그인 세션 ID
   * @return 인증에 유효하면 true
   */
  boolean isValidAccessSession(String userId, long sessionId);
}
