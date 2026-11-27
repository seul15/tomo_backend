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
class KakaoSocialProviderClientTest {
  @Mock private SocialOauthHttpClient http;
  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  @DisplayName("Kakao의 숫자형 고유 ID를 문자열로 변환한다")
  void readsKakaoUserId() throws Exception {
    // given
    when(http.postForm(eq("https://kauth.kakao.com/oauth/token"), any(MultiValueMap.class)))
        .thenReturn(mapper.readTree("{\"access_token\":\"kakao-access\"}"));
    when(http.getProfile("https://kapi.kakao.com/v2/user/me", "kakao-access"))
        .thenReturn(mapper.readTree("{\"id\":12345,\"kakao_account\":{}}"));
    KakaoSocialProviderClient client =
        new KakaoSocialProviderClient(http, "rest-api-key", "", "https://example.com/oauth/kakao");

    // when
    SocialProviderClient.Identity identity = client.authenticateAuthorizationCode("code", null);

    // then
    assertThat(identity.providerSubject()).isEqualTo("12345");

    verify(http)
        .postForm(
            eq("https://kauth.kakao.com/oauth/token"),
            argThat(
                form ->
                    "authorization_code".equals(form.getFirst("grant_type"))
                        && "rest-api-key".equals(form.getFirst("client_id"))
                        && "code".equals(form.getFirst("code"))
                        && "https://example.com/oauth/kakao"
                            .equals(form.getFirst("redirect_uri"))));
  }

  @Test
  @DisplayName("Kakao 사용자 정보에 ID가 없으면 성공 처리하지 않는다")
  void rejectsMissingKakaoId() throws Exception {
    // given
    when(http.postForm(eq("https://kauth.kakao.com/oauth/token"), any(MultiValueMap.class)))
        .thenReturn(mapper.readTree("{\"access_token\":\"kakao-access\"}"));
    when(http.getProfile("https://kapi.kakao.com/v2/user/me", "kakao-access"))
        .thenReturn(mapper.readTree("{\"kakao_account\":{}}"));
    KakaoSocialProviderClient client =
        new KakaoSocialProviderClient(http, "rest-api-key", "", "https://example.com/oauth/kakao");

    // when
    // then
    assertThatThrownBy(() -> client.authenticateAuthorizationCode("code", null))
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex -> assertThat(ex.getErrorCode()).isEqualTo(CommonErrorCode.INTERNAL_SERVER_ERROR));
  }

  @Test
  @DisplayName("인가 코드 누락이면 Kakao 서버와 통신하지 않는다")
  void rejectsBlankCode() {
    // given
    KakaoSocialProviderClient client =
        new KakaoSocialProviderClient(http, "rest-api-key", "", "https://example.com/oauth/kakao");

    // when
    // then
    assertThatThrownBy(() -> client.authenticateAuthorizationCode(" ", null))
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex -> assertThat(ex.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_REQUEST));
    verifyNoInteractions(http);
  }
}
