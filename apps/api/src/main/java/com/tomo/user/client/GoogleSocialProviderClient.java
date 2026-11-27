package com.tomo.user.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.tomo.common.exception.BusinessException;
import com.tomo.common.exception.CommonErrorCode;
import com.tomo.user.entity.SocialProvider;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/** Google 인가 코드와 PKCE로 소셜 사용자를 검증한다. */
@Component
public class GoogleSocialProviderClient implements SocialProviderClient {
  private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
  private static final String PROFILE_URL = "https://openidconnect.googleapis.com/v1/userinfo";
  private static final Pattern PKCE_PATTERN = Pattern.compile("[A-Za-z0-9._~-]{43,128}");

  private final SocialOauthHttpClient httpClient;
  private final String clientId;
  private final String clientSecret;
  private final String redirectUri;

  /**
   * Google OAuth 환경 설정을 주입한다.
   *
   * @param httpClient 공통 소셜 HTTP 클라이언트
   * @param clientId Google Client ID
   * @param clientSecret Google Client Secret. 사용하지 않을 수 있음
   * @param redirectUri Google 콘솔에 등록한 Redirect URI
   */
  public GoogleSocialProviderClient(
      SocialOauthHttpClient httpClient,
      @Value("${tomo.oauth.google.client-id:}") String clientId,
      @Value("${tomo.oauth.google.client-secret:}") String clientSecret,
      @Value("${tomo.oauth.google.redirect-uri:}") String redirectUri) {
    this.httpClient = httpClient;
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    this.redirectUri = redirectUri;
  }

  @Override
  public SocialProvider getProvider() {
    return SocialProvider.GOOGLE;
  }

  @Override
  public Identity authenticateAuthorizationCode(String authorizationCode, String codeVerifier) {
    if (authorizationCode == null
        || authorizationCode.isBlank()
        || codeVerifier == null
        || !PKCE_PATTERN.matcher(codeVerifier).matches()) {
      throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
    }
    if (clientId.isBlank() || redirectUri.isBlank()) {
      throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
    }

    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("grant_type", "authorization_code");
    form.add("client_id", clientId);
    if (!clientSecret.isBlank()) {
      form.add("client_secret", clientSecret);
    }
    form.add("redirect_uri", redirectUri);
    form.add("code", authorizationCode);
    form.add("code_verifier", codeVerifier);
    JsonNode token = httpClient.postForm(TOKEN_URL, form);
    JsonNode accessToken = token.path("access_token");
    if (!accessToken.isTextual() || accessToken.asText().isBlank()) {
      throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
    }
    return fetchProfile(accessToken.asText());
  }

  private Identity fetchProfile(String accessToken) {
    JsonNode profile = httpClient.getProfile(PROFILE_URL, accessToken);
    JsonNode sub = profile.path("sub");

    if (!sub.isTextual() || sub.asText().isBlank()) {
      throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
    }

    return new Identity(sub.asText());
  }
}
