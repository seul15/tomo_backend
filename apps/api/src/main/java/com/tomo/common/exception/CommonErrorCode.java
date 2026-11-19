package com.tomo.common.exception;

/** 여러 기능에서 공통으로 사용하는 오류 코드. */
public enum CommonErrorCode implements ErrorCode {
  INVALID_REQUEST(400, "요청 필드 또는 도메인 검증 조건이 올바르지 않습니다."),
  UNAUTHORIZED(401, "인증 정보가 없거나 유효하지 않습니다."),
  INVALID_ACCESS_TOKEN(401, "accessToken이 없거나 형식이 올바르지 않거나 만료·폐기되었습니다."),
  FORBIDDEN(403, "요청한 작업을 수행할 권한이 없습니다."),
  RESOURCE_NOT_FOUND(404, "요청한 리소스를 찾을 수 없습니다."),
  RESOURCE_CONFLICT(409, "현재 리소스 상태와 요청이 충돌합니다."),
  CONCURRENT_REQUEST_CONFLICT(409, "동일한 리소스에 대한 요청이 동시에 처리되어 충돌이 발생했습니다."),
  INTERNAL_SERVER_ERROR(500, "서버 처리 중 오류가 발생했습니다.");

  private final int httpStatus;
  private final String message;

  CommonErrorCode(int httpStatus, String message) {
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
