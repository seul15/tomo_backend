package com.tomo.user.dto.response;

import com.tomo.user.service.model.UserProfileResult;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 내 계정 및 프로필 조회 응답.
 *
 * @param nickname 닉네임
 * @param profileImageUrl 프로필 이미지 URL
 * @param email 연결된 이메일
 * @param provider 소셜 인증 제공자
 */
@Schema(description = "내 계정 및 프로필 조회 응답")
public record UserProfileResponse(
    @Schema(description = "사용자 닉네임", example = "집중토모", requiredMode = Schema.RequiredMode.REQUIRED)
        String nickname,
    @Schema(
            description = "프로필 이미지 URL. 미등록 시 null",
            example = "https://cdn.example.com/profile/user01.png",
            nullable = true,
            requiredMode = Schema.RequiredMode.REQUIRED)
        String profileImageUrl,
    @Schema(
            description = "연결된 이메일. 제공되지 않은 경우 null",
            example = "test@google.com",
            nullable = true,
            requiredMode = Schema.RequiredMode.REQUIRED)
        String email,
    @Schema(
            description = "소셜 인증 제공자 (GOOGLE 또는 KAKAO)",
            allowableValues = {"GOOGLE", "KAKAO"},
            example = "GOOGLE",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String provider) {

  /**
   * 서비스 결과를 HTTP 응답으로 변환한다.
   *
   * @param result 프로필 조회 결과
   * @return 프로필 조회 응답
   */
  public static UserProfileResponse from(UserProfileResult result) {
    return new UserProfileResponse(
        result.nickname(), result.profileImageUrl(), result.email(), result.provider().name());
  }
}
