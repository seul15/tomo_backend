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
}
