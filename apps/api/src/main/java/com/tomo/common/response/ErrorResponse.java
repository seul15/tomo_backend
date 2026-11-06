package com.tomo.common.response;

import com.tomo.common.exception.ErrorCode;

/**
 * API 공통 오류 응답.
 *
 * @param success 요청 성공 여부
 * @param error 오류 정보
 * @param meta 요청 메타데이터
 */
public record ErrorResponse(boolean success, Error error, ResponseMeta meta) {
  /**
   * 오류 응답을 생성한다.
   *
   * @param errorCode 발생한 오류 코드
   * @param requestId 요청 식별자
   * @return 공통 오류 응답
   */
  public static ErrorResponse from(ErrorCode errorCode, String requestId) {
    return new ErrorResponse(
        false, new Error(errorCode.name(), errorCode.getMessage()), ResponseMeta.from(requestId));
  }

  /**
   * 오류 코드와 메시지.
   *
   * @param code 오류 코드
   * @param message 오류 메시지
   */
  public record Error(String code, String message) {}
}
