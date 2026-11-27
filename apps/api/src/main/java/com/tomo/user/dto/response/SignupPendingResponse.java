package com.tomo.user.dto.response;

import com.tomo.user.service.model.SocialLoginResult;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 신규 또는 가입 대기 회원에게 반환하는 가입 전용 토큰.
 *
 * @param signupToken 가입 전용 JWT
 * @param signupTokenExpiresIn 가입 전용 토큰 만료기간(초)
 */
@Schema(description = "신규 또는 가입 대기 회원에게 반환하는 회원가입 전용 토큰")
public record SignupPendingResponse(
    @Schema(description = "가입 전용 JWT", requiredMode = Schema.RequiredMode.REQUIRED)
        String signupToken,
    @Schema(
            description = "가입 전용 토큰 만료기간(초)",
            example = "600",
            requiredMode = Schema.RequiredMode.REQUIRED)
        long signupTokenExpiresIn)
    implements SocialLoginResponse {

  /**
   * 가입 대기 업무 결과를 응답으로 변환한다.
   *
   * @param result 소셜 로그인 업무 결과
   * @return 가입 대기 HTTP 응답
   */
  public static SignupPendingResponse from(SocialLoginResult result) {
    return new SignupPendingResponse(result.signupToken(), result.signupTokenExpiresIn());
  }
}
