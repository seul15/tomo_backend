package com.tomo.common.auth.exception;

import com.tomo.common.exception.ErrorCode;

/** 인증 및 로그인 세션에서 사용하는 오류 코드. */
public enum AuthErrorCode implements ErrorCode {
  /** accessToken이 없거나 형식이 올바르지 않거나 만료·폐기되었습니다. */
  INVALID_ACCESS_TOKEN(401, "accessToken이 없거나 형식이 올바르지 않거나 만료·폐기되었습니다."),

  /** Refresh Token이 없거나 형식이 올바르지 않거나, 만료·폐기·교체되어 사용할 수 없습니다. */
  INVALID_REFRESH_TOKEN(401, "Refresh Token이 없거나 형식이 올바르지 않거나, 만료·폐기·교체되어 사용할 수 없습니다."),

  /** signupToken이 없거나 형식이 올바르지 않거나 만료·폐기되었습니다. */
  INVALID_SIGNUP_TOKEN(401, "signupToken이 없거나 형식이 올바르지 않거나 만료·폐기되었습니다."),

  /** 소셜 인증에 실패했거나 인가 코드가 만료되었습니다. */
  OAUTH_AUTHENTICATION_FAILED(401, "소셜 인증에 실패했거나 인가 코드가 만료되었습니다."),

  /** 비활성화된 계정은 인증 토큰을 재발급할 수 없습니다. */
  TOKEN_REFRESH_FORBIDDEN(403, "비활성화된 계정은 인증 토큰을 재발급할 수 없습니다."),

  /** 이용이 제한된 계정은 소셜 로그인을 진행할 수 없습니다. */
  SOCIAL_LOGIN_FORBIDDEN(403, "이용이 제한된 계정은 소셜 로그인을 진행할 수 없습니다."),

  /** 동일 세션의 로그아웃과 토큰 재발급이 동시에 처리되어 상태가 충돌했습니다. */
  SESSION_STATE_CONFLICT(409, "동일 세션의 로그아웃과 토큰 재발급이 동시에 처리되어 상태가 충돌했습니다.");

  private final int httpStatus;
  private final String message;

  /**
   * 오류의 HTTP 상태 코드와 메시지를 설정한다.
   *
   * @param httpStatus HTTP 상태 코드
   * @param message 클라이언트에 전달할 오류 메시지
   */
  AuthErrorCode(int httpStatus, String message) {
    this.httpStatus = httpStatus;
    this.message = message;
  }

  /**
   * 오류 코드 이름을 반환한다.
   *
   * @return 오류 코드 이름
   */
  @Override
  public String code() {
    return name();
  }

  /**
   * 오류 메시지를 반환한다.
   *
   * @return 클라이언트에 전달할 오류 메시지
   */
  @Override
  public String message() {
    return message;
  }

  /**
   * 오류의 HTTP 상태 코드를 반환한다.
   *
   * @return HTTP 상태 코드
   */
  @Override
  public int httpStatus() {
    return httpStatus;
  }
}
