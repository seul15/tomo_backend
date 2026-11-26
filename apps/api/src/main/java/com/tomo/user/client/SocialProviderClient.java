package com.tomo.user.client;

import com.tomo.user.entity.SocialProvider;

/** 소셜 인증 제공자별 사용자 식별 정보 조회 계약. */
public interface SocialProviderClient {

  /**
   * 해당 클라이언트가 처리하는 소셜 제공자를 반환한다.
   *
   * @return 소셜 제공자
   */
  SocialProvider getProvider();

  /**
   * 제공자의 Access Token으로 사용자 식별값을 조회한다.
   *
   * @param providerAccessToken 소셜 제공자가 발급한 Access Token
   * @return 제공자별 사용자 고유 식별값
   */
  String getProviderSubject(String providerAccessToken);

  /**
   * 인가 코드를 교환해 제공자가 검증한 사용자 정보만 반환한다.
   *
   * @param authorizationCode 제공자 인가 코드
   * @param codeVerifier Google PKCE 검증값. Kakao에서는 null
   * @return 제공자 고유 ID와 선택 이메일
   */
  Identity authenticateAuthorizationCode(String authorizationCode, String codeVerifier);

  /**
   * 검증된 제공자 사용자 식별 정보.
   *
   * @param providerSubject 제공자 고유 사용자 ID
   * @param email 제공된 이메일. 없으면 null
   */
  record Identity(String providerSubject, String email) {}
}
