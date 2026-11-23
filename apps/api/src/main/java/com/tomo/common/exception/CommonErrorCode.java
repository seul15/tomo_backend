package com.tomo.common.exception;

/** 여러 기능에서 공통으로 사용하는 오류 코드. */
public enum CommonErrorCode implements ErrorCode {
  /** 요청 필드 또는 도메인 검증 조건을 충족하지 못한 경우. */
  INVALID_REQUEST(400, "요청 필드 또는 도메인 검증 조건이 올바르지 않습니다."),

  /** 인증 정보가 없거나 유효하지 않은 경우. */
  UNAUTHORIZED(401, "인증 정보가 없거나 유효하지 않습니다."),

  /** 인증된 사용자에게 요청한 작업의 권한이 없는 경우. */
  FORBIDDEN(403, "요청한 작업을 수행할 권한이 없습니다."),

  /** 조회하거나 변경할 리소스가 존재하지 않는 경우. */
  RESOURCE_NOT_FOUND(404, "요청한 리소스를 찾을 수 없습니다."),

  /** 리소스의 현재 상태와 요청한 작업이 충돌하는 경우. */
  RESOURCE_CONFLICT(409, "현재 리소스 상태와 요청이 충돌합니다."),

  /** 동일 리소스에 대한 요청이 동시에 처리되어 충돌한 경우. */
  CONCURRENT_REQUEST_CONFLICT(409, "동일한 리소스에 대한 요청이 동시에 처리되어 충돌이 발생했습니다."),

  /** 예상하지 못한 서버 내부 오류가 발생한 경우. */
  INTERNAL_SERVER_ERROR(500, "서버 처리 중 오류가 발생했습니다.");

  private final int httpStatus;
  private final String message;

  /**
   * 공통 오류 코드의 HTTP 상태와 메시지를 설정한다.
   *
   * @param httpStatus HTTP 상태 코드
   * @param message 클라이언트에 전달할 오류 메시지
   */
  CommonErrorCode(int httpStatus, String message) {
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
   * 오류에 대응하는 HTTP 상태 코드를 반환한다.
   *
   * @return HTTP 상태 코드
   */
  @Override
  public int httpStatus() {
    return httpStatus;
  }
}
