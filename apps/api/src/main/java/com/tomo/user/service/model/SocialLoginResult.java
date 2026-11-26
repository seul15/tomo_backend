package com.tomo.user.service.model;

/**
 * PENDING Signup Token 또는 ACTIVE 로그인 토큰을 담는 소셜 로그인 업무 결과.
 *
 * @param signupToken 신규·가입 대기 회원의 Signup JWT. ACTIVE이면 null
 * @param signupTokenExpiresIn Signup Token TTL(초). ACTIVE이면 0
 * @param tokens ACTIVE 회원의 Access·Refresh Token. PENDING이면 null
 * @param nickname ACTIVE 회원의 닉네임
 * @param profileImageUrl ACTIVE 회원의 프로필 이미지 URL
 */
public record SocialLoginResult(
    String signupToken,
    long signupTokenExpiresIn,
    TokenRefreshResult tokens,
    String nickname,
    String profileImageUrl) {

  /**
   * PENDING과 ACTIVE 결과의 필드가 섞이지 않도록 검증한다.
   *
   * @param signupToken 가입 전용 토큰
   * @param signupTokenExpiresIn 가입 토큰 만료기간
   * @param tokens 로그인 완료 토큰 쌍
   * @param nickname 가입 완료 닉네임
   * @param profileImageUrl 프로필 이미지 URL
   * @throws IllegalArgumentException 결과 상태에 맞지 않는 필드 조합일 때
   */
  public SocialLoginResult {
    boolean pending = signupToken != null && !signupToken.isBlank();
    if (pending) {
      if (signupTokenExpiresIn <= 0
          || tokens != null
          || nickname != null
          || profileImageUrl != null) {
        throw new IllegalArgumentException("가입 대기 결과의 토큰 필드가 올바르지 않습니다.");
      }
    } else if (signupToken != null
        || signupTokenExpiresIn != 0
        || tokens == null
        || nickname == null
        || nickname.isBlank()
        || profileImageUrl == null
        || profileImageUrl.isBlank()) {
      throw new IllegalArgumentException("로그인 완료 결과의 필드가 올바르지 않습니다.");
    }
  }

  /**
   * 회원가입 대기 응답을 만든다.
   *
   * @param token 가입 전용 JWT
   * @param expiresIn 만료기간(초)
   * @return PENDING 결과
   */
  public static SocialLoginResult pending(String token, long expiresIn) {
    return new SocialLoginResult(token, expiresIn, null, null, null);
  }

  /**
   * 가입 완료 사용자의 로그인 결과를 만든다.
   *
   * @param tokens Access·Refresh Token 및 TTL
   * @param nickname 사용자 닉네임
   * @param profileImageUrl 프로필 이미지 URL
   * @return ACTIVE 결과
   */
  public static SocialLoginResult active(
      TokenRefreshResult tokens, String nickname, String profileImageUrl) {
    return new SocialLoginResult(null, 0L, tokens, nickname, profileImageUrl);
  }
}
