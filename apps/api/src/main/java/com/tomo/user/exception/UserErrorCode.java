package com.tomo.user.exception;

import com.tomo.common.exception.ErrorCode;

/** 회원 기능에서 사용하는 오류 코드. */
public enum UserErrorCode implements ErrorCode {
  PROFILE_VIEW_FORBIDDEN(403, "해당 계정의 프로필을 조회할 권한이 없습니다.");

  private final int httpStatus;
  private final String message;

  UserErrorCode(int httpStatus, String message) {
    this.httpStatus = httpStatus;
    this.message = message;
  }

  @Override
  public String code() {
    return name();
  }

  @Override
  public String message() {
    return message;
  }

  @Override
  public int httpStatus() {
    return httpStatus;
  }
}
