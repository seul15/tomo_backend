package com.tomo.user.dto.response;

import com.tomo.user.service.model.SocialLoginResult;
import com.tomo.user.service.model.TokenRefreshResult;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 가입 완료 사용자의 소셜 로그인 성공 응답.
 *
 * @param accessToken TOMO Access JWT
 * @param refreshToken 불투명 Refresh Token
 * @param accessTokenExpiresIn Access Token 만료기간(초)
 * @param refreshTokenExpiresIn Refresh Token 만료기간(초)
 * @param user 가입 완료 사용자 정보
 */
@Schema(description = "가입 완료 회원의 소셜 로그인 성공 응답 데이터")
public record AuthenticatedUserResponse(
    @Schema(description = "TOMO Access JWT", requiredMode = Schema.RequiredMode.REQUIRED)
        String accessToken,
    @Schema(description = "불투명 Refresh Token", requiredMode = Schema.RequiredMode.REQUIRED)
        String refreshToken,
    @Schema(
            description = "Access Token 만료기간(초)",
            example = "3600",
            requiredMode = Schema.RequiredMode.REQUIRED)
        long accessTokenExpiresIn,
    @Schema(
            description = "Refresh Token 만료기간(초)",
            example = "604800",
            requiredMode = Schema.RequiredMode.REQUIRED)
        long refreshTokenExpiresIn,
    @Schema(description = "가입 완료 사용자", requiredMode = Schema.RequiredMode.REQUIRED) UserInfo user)
    implements SocialLoginResponse {

  /**
   * 토큰 발급 업무 결과를 HTTP 응답으로 변환한다.
   *
   * @param result 가입 완료 소셜 로그인 결과
   * @return 로그인 완료 응답
   */
  public static AuthenticatedUserResponse from(SocialLoginResult result) {
    TokenRefreshResult tokens = result.tokens();
    return new AuthenticatedUserResponse(
        tokens.accessToken(),
        tokens.refreshToken(),
        tokens.accessTokenExpiresIn(),
        tokens.refreshTokenExpiresIn(),
        new UserInfo("USER", result.nickname(), result.profileImageUrl()));
  }

  /**
   * 로그인 완료 사용자 프로필.
   *
   * @param role TOMO 역할
   * @param nickname 가입 완료 닉네임
   * @param profileImageUrl 기본 이미지 또는 등록 이미지 URL
   */
  @Schema(description = "소셜 로그인에 성공한 회원의 기본 프로필 정보")
  public record UserInfo(
      @Schema(
              description = "TOMO 사용자 역할",
              allowableValues = {"USER"},
              example = "USER",
              requiredMode = Schema.RequiredMode.REQUIRED)
          String role,
      @Schema(description = "닉네임", requiredMode = Schema.RequiredMode.REQUIRED) String nickname,
      @Schema(description = "프로필 이미지 URL", requiredMode = Schema.RequiredMode.REQUIRED)
          String profileImageUrl) {}
}
