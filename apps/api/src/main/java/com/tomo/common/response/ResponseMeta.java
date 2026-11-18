package com.tomo.common.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * API 응답 공통 메타데이터.
 *
 * @param requestId 요청 식별자
 * @param timestamp 응답 생성 시각
 */
@Schema(description = "요청 메타데이터")
public record ResponseMeta(
    @Schema(
            description = "요청 식별자",
            example = "example-request-id",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String requestId,
    @Schema(
            description = "응답 생성 시각 (UTC)",
            example = "2026-11-19T00:00:00Z",
            requiredMode = Schema.RequiredMode.REQUIRED)
        OffsetDateTime timestamp) {
  /**
   * 요청 식별자를 이용해 응답 메타데이터를 생성한다.
   *
   * @param requestId 요청 식별자
   * @return 응답 메타데이터
   */
  public static ResponseMeta from(String requestId) {
    return new ResponseMeta(requestId, OffsetDateTime.now(ZoneOffset.UTC));
  }
}
