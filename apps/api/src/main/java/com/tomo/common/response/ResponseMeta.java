package com.tomo.common.response;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * API 응답 공통 메타데이터.
 *
 * @param requestId 요청 식별자
 * @param timestamp 응답 생성 시각
 */
public record ResponseMeta(String requestId, OffsetDateTime timestamp) {
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
