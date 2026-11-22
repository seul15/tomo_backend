package com.tomo.user.dto.response;

import com.tomo.user.service.model.TokenRefreshResult;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 인증 토큰 재발급 성공 응답.
 *
 * @param accessToken 새 Access JWT
 * @param refreshToken 새 불투명 Refresh Token
 * @param accessTokenExpiresIn Access Token 유효기간(초)
 * @param refreshTokenExpiresIn Refresh Token 유효기간(초)
 * @param user 인증된 사용자 역할 정보
 */
@Schema(description = "인증 토큰 재발급 성공 응답 데이터")
public record AuthRefreshResponse(
    @Schema(
            description = "API 호출에 사용할 새 Access JWT",
            example = "eyJhbGciOiJIUzI1NiJ9.example",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String accessToken,
    @Schema(
            description = "기존 Refresh Token을 교체하여 발급한 새 불투명 Refresh Token",
            example = "123.RANDOM_TOKEN_VALUE",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String refreshToken,
    @Schema(
            description = "Access Token 유효기간(초). 기본값 3600초(1시간)",
            example = "3600",
            requiredMode = Schema.RequiredMode.REQUIRED)
        long accessTokenExpiresIn,
    @Schema(
            description = "Refresh Token 유효기간(초). 기본값 604800초(7일)",
            example = "604800",
            requiredMode = Schema.RequiredMode.REQUIRED)
        long refreshTokenExpiresIn,
    @Schema(description = "인증된 사용자의 역할 정보", requiredMode = Schema.RequiredMode.REQUIRED)
        UserInfo user) {

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
   * @param role 계정 역할
   */
  @Schema(description = "인증된 사용자 역할")
  public record UserInfo(
      @Schema(
              description = "계정 역할",
              allowableValues = {"USER"},
              example = "USER",
              requiredMode = Schema.RequiredMode.REQUIRED)
          String role) {}
}
