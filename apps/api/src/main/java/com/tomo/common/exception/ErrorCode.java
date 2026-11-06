package com.tomo.common.exception;

/** 공통 API 오류 코드와 메시지 및 HTTP 상태를 정의합니다. */
public enum ErrorCode {
  INVALID_REQUEST(400, "요청 필드 또는 도메인 검증 조건이 올바르지 않습니다."),
  UNAUTHORIZED(401, "인증 정보가 없거나 유효하지 않습니다."),
  INVALID_ACCESS_TOKEN(401, "accessToken이 없거나 형식이 올바르지 않거나 만료·폐기되었습니다."),
  RESOURCE_NOT_FOUND(404, "요청한 리소스를 찾을 수 없습니다."),
  RESOURCE_CONFLICT(409, "현재 리소스 상태와 요청이 충돌합니다."),
  CONCURRENT_REQUEST_CONFLICT(409, "동일한 리소스에 대한 요청이 동시에 처리되어 충돌이 발생했습니다."),
  INTERNAL_SERVER_ERROR(500, "서버 처리 중 오류가 발생했습니다.");

  private final int httpStatus;
  private final String message;

  ErrorCode(int httpStatus, String message) {
    this.httpStatus = httpStatus;
    this.message = message;
  }

  public int getHttpStatus() {
    return httpStatus;
  }

  public String getMessage() {
    return message;
  }
}
