package com.tomo.user.exception;

import com.tomo.common.exception.ErrorCode;

/** 회원·프로필 기능에서 사용하는 오류 코드. */
public enum UserErrorCode implements ErrorCode {
  /** 닉네임이 형식 또는 유효성 검증 조건을 충족하지 않습니다. */
  INVALID_NICKNAME(400, "닉네임이 형식 또는 유효성 검증 조건을 충족하지 않습니다."),

  /** 본인의 계정이 아닌 다른 사용자의 프로필을 등록할 수 없습니다. */
  PROFILE_CREATE_FORBIDDEN(403, "본인의 계정이 아닌 다른 사용자의 프로필을 등록할 수 없습니다."),

  /** 프로필을 수정할 권한이 없습니다. */
  PROFILE_UPDATE_FORBIDDEN(403, "프로필을 수정할 권한이 없습니다."),

  /** 해당 계정의 프로필을 조회할 권한이 없습니다. */
  PROFILE_VIEW_FORBIDDEN(403, "해당 계정의 프로필을 조회할 권한이 없습니다."),

  /** 다른 사용자의 계정을 탈퇴시킬 권한이 없습니다. */
  ACCOUNT_DELETION_FORBIDDEN(403, "다른 사용자의 계정을 탈퇴시킬 권한이 없습니다."),

  /** 이미 탈퇴 처리된 회원입니다. */
  ACCOUNT_ALREADY_WITHDRAWN(409, "이미 탈퇴 처리된 회원입니다.");

  private final int httpStatus;
  private final String message;

  /**
   * 오류의 HTTP 상태 코드와 메시지를 설정한다.
   *
   * @param httpStatus HTTP 상태 코드
   * @param message 클라이언트에 전달할 오류 메시지
   */
  UserErrorCode(int httpStatus, String message) {
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
