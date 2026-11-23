package com.tomo.user.exception;

import com.tomo.common.exception.ErrorCode;

/** 회원 기능에서 사용하는 오류 코드. */
public enum UserErrorCode implements ErrorCode {
  PROFILE_VIEW_FORBIDDEN(403, "해당 계정의 프로필을 조회할 권한이 없습니다."),
  INVALID_REFRESH_TOKEN(401, "Refresh Token이 없거나 형식이 올바르지 않거나, 만료·폐기·교체되어 사용할 수 없습니다."),
  TOKEN_REFRESH_FORBIDDEN(403, "비활성화된 계정은 인증 토큰을 재발급할 수 없습니다.");

  private final int httpStatus;
  private final String message;

  UserErrorCode(int httpStatus, String message) {
    this.httpStatus = httpStatus;
    this.message = message;
  }

  /**
   * 오류 코드 이름을 반환한다.
   *
   * @return 오류 코드
   */
  @Override
  public String code() {
    return name();
  }

  /**
   * 오류 메시지를 반환한다.
   *
   * @return 오류 메시지
   */
  @Override
  public String message() {
    return message;
  }

  /**
   * HTTP 상태 코드를 반환한다.
   *
   * @return HTTP 상태 코드
   */
  @Override
  public int httpStatus() {
    return httpStatus;
  }
}
