package com.tomo.common.exception;

/** API 오류 코드의 공통 계약. */
public interface ErrorCode {

  /**
   * 클라이언트에 전달할 오류 코드를 반환한다.
   *
   * @return 오류 코드
   */
  String code();

  /**
   * 클라이언트에 전달할 오류 메시지를 반환한다.
   *
   * @return 오류 메시지
   */
  String message();

  /**
   * 오류의 HTTP 상태 코드를 반환한다.
   *
   * @return HTTP 상태 코드
   */
  int httpStatus();
}
