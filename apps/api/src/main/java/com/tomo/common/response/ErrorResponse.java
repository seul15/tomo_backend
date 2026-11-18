package com.tomo.common.response;

import com.tomo.common.exception.ErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * API 공통 오류 응답.
 *
 * @param success 요청 성공 여부
 * @param error 오류 정보
 * @param meta 요청 메타데이터
 */
@Schema(description = "공통 오류 응답")
public record ErrorResponse(
    @Schema(
            description = "요청 성공 여부",
            example = "false",
            requiredMode = Schema.RequiredMode.REQUIRED)
        boolean success,
    @Schema(description = "오류 정보", requiredMode = Schema.RequiredMode.REQUIRED) Error error,
    @Schema(description = "요청 메타데이터", requiredMode = Schema.RequiredMode.REQUIRED)
        ResponseMeta meta) {
  /**
   * 오류 응답을 생성한다.
   *
   * @param errorCode 발생한 오류 코드
   * @param requestId 요청 식별자
   * @return 공통 오류 응답
   */
  public static ErrorResponse from(ErrorCode errorCode, String requestId) {
    return new ErrorResponse(
        false, new Error(errorCode.code(), errorCode.message()), ResponseMeta.from(requestId));
  }

  /**
   * 오류 코드와 메시지.
   *
   * @param code 오류 코드
   * @param message 오류 메시지
   */
  @Schema(description = "오류 코드 및 메시지")
  public record Error(
      @Schema(
              description = "오류 코드",
              example = "INVALID_ACCESS_TOKEN",
              requiredMode = Schema.RequiredMode.REQUIRED)
          String code,
      @Schema(
              description = "오류 메시지",
              example = "accessToken이 없거나 형식이 올바르지 않거나 만료·폐기되었습니다.",
              requiredMode = Schema.RequiredMode.REQUIRED)
          String message) {}
}
