package com.tomo.user.client;

import com.tomo.user.entity.SocialProvider;

/** 소셜 인증 제공자별 인가 코드 검증 계약. */
public interface SocialProviderClient {

  /**
   * 해당 클라이언트가 처리하는 소셜 제공자를 반환한다.
   *
   * @return 소셜 제공자
   */
  SocialProvider getProvider();

  /**
   * 인가 코드를 제공자 토큰으로 교환하고 사용자 식별값을 조회한다.
   *
   * @param authorizationCode 제공자 인가 코드
   * @param codeVerifier Google PKCE 검증값. Kakao에서는 null
   * @return 검증된 제공자 사용자 식별 정보
   */
  Identity authenticateAuthorizationCode(String authorizationCode, String codeVerifier);

  /**
   * 검증된 소셜 사용자 식별 정보.
   *
   * @param providerSubject 제공자 고유 사용자 ID
   */
  record Identity(String providerSubject) {}
}
