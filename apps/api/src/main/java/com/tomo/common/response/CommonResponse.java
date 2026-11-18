package com.tomo.common.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * API 공통 성공 응답.
 *
 * @param <T> 응답 데이터 타입
 * @param success 요청 성공 여부
 * @param data 응답 데이터
 * @param meta 요청 메타데이터
 */
@Schema(description = "공통 성공 응답")
public record CommonResponse<T>(
    @Schema(description = "요청 성공 여부", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean success,
    @Schema(description = "API 응답 데이터", requiredMode = Schema.RequiredMode.REQUIRED) T data,
    @Schema(description = "요청 메타데이터", requiredMode = Schema.RequiredMode.REQUIRED)
        ResponseMeta meta) {
  /**
   * 성공 응답을 생성한다.
   *
   * @param data 응답 데이터
   * @param requestId 요청 식별자
   * @param <T> 응답 데이터 타입
   * @return 성공 응답
   */
  public static <T> CommonResponse<T> success(T data, String requestId) {
    return new CommonResponse<>(true, data, ResponseMeta.from(requestId));
  }
}
