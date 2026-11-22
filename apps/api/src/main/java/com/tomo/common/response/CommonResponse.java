package com.tomo.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * API 공통 성공 응답.
 *
 * @param <T> 응답 데이터 타입
 * @param success 요청 성공 여부
 * @param data 응답 데이터. 데이터가 없는 성공 응답에서는 null
 * @param meta 요청 메타데이터
 */
@Schema(description = "공통 성공 응답")
public record CommonResponse<T>(
    @Schema(description = "요청 성공 여부", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean success,
    @Schema(description = "API 응답 데이터", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @JsonInclude(JsonInclude.Include.NON_NULL)
        T data,
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

  /**
   * 데이터 본문 없이 성공 여부와 요청 메타데이터만 반환한다.
   *
   * @param requestId 요청 추적 ID
   * @return data 필드가 생략되는 성공 응답
   */
  public static CommonResponse<Void> successWithoutData(String requestId) {
    return new CommonResponse<>(true, null, ResponseMeta.from(requestId));
  }
}
