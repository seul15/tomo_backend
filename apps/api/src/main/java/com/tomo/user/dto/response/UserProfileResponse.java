package com.tomo.user.dto.response;

import com.tomo.user.service.model.UserProfileResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Locale;

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
    @Schema(description = "사용자 닉네임", example = "집중토모") String nickname,
    @Schema(description = "프로필 이미지 URL", nullable = true) String profileImageUrl,
    @Schema(description = "연결된 이메일", nullable = true) String email,
    @Schema(description = "소셜 인증 제공자", example = "google") String provider) {

  /**
   * 서비스 결과를 HTTP 응답으로 변환한다.
   *
   * @param result 프로필 조회 결과
   * @return 프로필 조회 응답
   */
  public static UserProfileResponse from(UserProfileResult result) {
    return new UserProfileResponse(
        result.nickname(),
        result.profileImageUrl(),
        result.email(),
        result.provider().name().toLowerCase(Locale.ROOT));
  }
}
