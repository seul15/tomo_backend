package com.tomo.user.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tomo.common.exception.BusinessException;
import com.tomo.common.exception.CommonErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.MultiValueMap;

@ExtendWith(MockitoExtension.class)
class GoogleSocialProviderClientTest {
  @Mock private SocialOauthHttpClient http;
  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  @DisplayName("Google에 인가 코드와 PKCE verifier를 전달하고 사용자 sub를 읽는다")
  void exchangesCodeWithPkce() throws Exception {
    // given
    when(http.postForm(eq("https://oauth2.googleapis.com/token"), any(MultiValueMap.class)))
        .thenReturn(mapper.readTree("{\"access_token\":\"google-access\"}"));
    when(http.getProfile("https://openidconnect.googleapis.com/v1/userinfo", "google-access"))
        .thenReturn(mapper.readTree("{\"sub\":\"google-1\",\"email\":\"user@example.com\"}"));
    GoogleSocialProviderClient client =
        new GoogleSocialProviderClient(http, "client-id", "", "https://example.com/oauth/google");

    // when
    SocialProviderClient.Identity identity =
        client.authenticateAuthorizationCode("code", "A".repeat(43));

    // then
    assertThat(identity.providerSubject()).isEqualTo("google-1");
    verify(http)
        .postForm(
            eq("https://oauth2.googleapis.com/token"),
            argThat(
                form ->
                    "A".repeat(43).equals(form.getFirst("code_verifier"))
                        && "https://example.com/oauth/google"
                            .equals(form.getFirst("redirect_uri"))));
  }

  @Test
  @DisplayName("PKCE 검증값 형식 오류는 Google API를 호출하지 않고 400 처리한다")
  void rejectsInvalidVerifier() {
    // given
    GoogleSocialProviderClient client =
        new GoogleSocialProviderClient(http, "client-id", "", "https://example.com/oauth/google");

    // when
    // then
    assertThatThrownBy(() -> client.authenticateAuthorizationCode("code", "too-short"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex -> assertThat(ex.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_REQUEST));
    verifyNoInteractions(http);
  }

  @Test
  @DisplayName("Google 설정이 없으면 사용자 인증 실패가 아닌 서버 오류로 분류한다")
  void rejectsMissingConfig() {
    // given
    GoogleSocialProviderClient client =
        new GoogleSocialProviderClient(http, "", "", "https://example.com/oauth/google");

    // when
    // then
    assertThatThrownBy(() -> client.authenticateAuthorizationCode("code", "A".repeat(43)))
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex -> assertThat(ex.getErrorCode()).isEqualTo(CommonErrorCode.INTERNAL_SERVER_ERROR));
    verifyNoInteractions(http);
  }
}
