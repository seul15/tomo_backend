package com.tomo.user.dto.response;

import com.tomo.user.service.model.TokenRefreshResult;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Refresh Token 교체 성공 응답.
 *
 * @param accessToken 새 Access JWT
 * @param refreshToken 새 불투명 Refresh Token
 * @param accessTokenExpiresIn Access JWT 유효기간(초)
 * @param refreshTokenExpiresIn Refresh Token 유효기간(초)
 * @param user 사용자 역할 정보
 */
@Schema(description = "인증 토큰 재발급 결과")
public record AuthRefreshResponse(
    @Schema(description = "새 Access Token") String accessToken,
    @Schema(description = "새 Refresh Token") String refreshToken,
    @Schema(description = "Access Token 만료시간(초)", example = "3600") long accessTokenExpiresIn,
    @Schema(description = "Refresh Token 만료시간(초)", example = "604800") long refreshTokenExpiresIn,
    @Schema(description = "사용자 역할") UserInfo user) {
  /**
   * Service 결과를 HTTP 응답으로 변환한다.
   *
   * @param result 검증·교체가 완료된 토큰 결과
   * @return API 응답 DTO
   */
  public static AuthRefreshResponse from(TokenRefreshResult result) {
    return new AuthRefreshResponse(
        result.accessToken(),
        result.refreshToken(),
        result.accessTokenExpiresIn(),
        result.refreshTokenExpiresIn(),
        new UserInfo("USER"));
  }

  /**
   * 사용자 역할 응답.
   *
   * @param role 일반 사용자 역할
   */
  public record UserInfo(@Schema(example = "USER") String role) {}
}
