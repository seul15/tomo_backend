package com.tomo.user.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.tomo.common.exception.BusinessException;
import com.tomo.common.exception.CommonErrorCode;
import com.tomo.user.entity.SocialProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/** Kakao 인가 코드로 소셜 사용자를 검증한다. */
@Component
public class KakaoSocialProviderClient implements SocialProviderClient {
  private static final String TOKEN_URL = "https://kauth.kakao.com/oauth/token";
  private static final String PROFILE_URL = "https://kapi.kakao.com/v2/user/me";

  private final SocialOauthHttpClient httpClient;
  private final String restApiKey;
  private final String clientSecret;
  private final String redirectUri;

  /**
   * Kakao OAuth 환경 설정을 주입한다.
   *
   * @param httpClient 공통 소셜 HTTP 클라이언트
   * @param restApiKey Kakao REST API 키
   * @param clientSecret Kakao Client Secret. 사용하지 않을 수 있음
   * @param redirectUri Kakao 콘솔에 등록한 Redirect URI
   */
  public KakaoSocialProviderClient(
      SocialOauthHttpClient httpClient,
      @Value("${tomo.oauth.kakao.rest-api-key:}") String restApiKey,
      @Value("${tomo.oauth.kakao.client-secret:}") String clientSecret,
      @Value("${tomo.oauth.kakao.redirect-uri:}") String redirectUri) {
    this.httpClient = httpClient;
    this.restApiKey = restApiKey;
    this.clientSecret = clientSecret;
    this.redirectUri = redirectUri;
  }

  @Override
  public SocialProvider getProvider() {
    return SocialProvider.KAKAO;
  }

  @Override
  public Identity authenticateAuthorizationCode(String authorizationCode, String unusedVerifier) {
    if (authorizationCode == null || authorizationCode.isBlank()) {
      throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
    }
    if (restApiKey.isBlank() || redirectUri.isBlank()) {
      throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
    }

    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("grant_type", "authorization_code");
    form.add("client_id", restApiKey);
    if (!clientSecret.isBlank()) {
      form.add("client_secret", clientSecret);
    }
    form.add("redirect_uri", redirectUri);
    form.add("code", authorizationCode);
    JsonNode token = httpClient.postForm(TOKEN_URL, form);
    JsonNode accessToken = token.path("access_token");
    if (!accessToken.isTextual() || accessToken.asText().isBlank()) {
      throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
    }
    return fetchProfile(accessToken.asText());
  }

  private Identity fetchProfile(String accessToken) {
    JsonNode profile = httpClient.getProfile(PROFILE_URL, accessToken);
    JsonNode id = profile.path("id");

    if ((!id.isTextual() && !id.isIntegralNumber()) || id.asText().isBlank()) {
      throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
    }

    return new Identity(id.asText());
  }
}
