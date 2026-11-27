package com.tomo.user.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tomo.common.auth.exception.AuthErrorCode;
import com.tomo.common.exception.BusinessException;
import com.tomo.common.exception.CommonErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

class SocialOauthHttpClientTest {
  @Test
  @DisplayName("OAuth 서버 invalid_grant는 만료 또는 재사용된 인가 코드 401로 변환한다")
  void invalidGrantIsAuthFailure() {
    // given
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo("https://example.com/token"))
        .andRespond(
            withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":\"invalid_grant\"}"));
    SocialOauthHttpClient client = new SocialOauthHttpClient(builder.build(), new ObjectMapper());

    // when
    // then
    assertThatThrownBy(
            () -> client.postForm("https://example.com/token", new LinkedMultiValueMap<>()))
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex ->
                assertThat(ex.getErrorCode()).isEqualTo(AuthErrorCode.OAUTH_AUTHENTICATION_FAILED));
    server.verify();
  }

  @Test
  @DisplayName("OAuth 서버 invalid_client는 사용자가 아닌 서버 설정 오류 500으로 분류한다")
  void invalidClientIsServerFailure() {
    // given
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo("https://example.com/token"))
        .andRespond(
            withStatus(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":\"invalid_client\"}"));
    SocialOauthHttpClient client = new SocialOauthHttpClient(builder.build(), new ObjectMapper());

    // when
    // then
    assertThatThrownBy(
            () -> client.postForm("https://example.com/token", new LinkedMultiValueMap<>()))
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex -> assertThat(ex.getErrorCode()).isEqualTo(CommonErrorCode.INTERNAL_SERVER_ERROR));
    server.verify();
  }

  @Test
  @DisplayName("OAuth 제공자가 제한 응답 429를 보내면 성공 처리하지 않는다")
  void rateLimitIsServerFailure() {
    // given
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo("https://example.com/token"))
        .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
    SocialOauthHttpClient client = new SocialOauthHttpClient(builder.build(), new ObjectMapper());

    // when
    // then
    assertThatThrownBy(
            () -> client.postForm("https://example.com/token", new LinkedMultiValueMap<>()))
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex -> assertThat(ex.getErrorCode()).isEqualTo(CommonErrorCode.INTERNAL_SERVER_ERROR));
    server.verify();
  }

  @Test
  @DisplayName("정상 HTTP 200이더라도 본문이 JSON 객체가 아니면 500으로 처리한다")
  void invalidResponseSchemaIsServerFailure() {
    // given
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo("https://example.com/token"))
        .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body("[]"));
    SocialOauthHttpClient client = new SocialOauthHttpClient(builder.build(), new ObjectMapper());

    // when
    // then
    assertThatThrownBy(
            () -> client.postForm("https://example.com/token", new LinkedMultiValueMap<>()))
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex -> assertThat(ex.getErrorCode()).isEqualTo(CommonErrorCode.INTERNAL_SERVER_ERROR));
    server.verify();
  }

  @Test
  @DisplayName("OAuth 토큰 교환 요청은 URL 인코딩 Form으로 전송한다")
  void sendsFormEncodedTokenRequest() {
    // given
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    server
        .expect(requestTo("https://example.com/token"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Content-Type", MediaType.APPLICATION_FORM_URLENCODED_VALUE))
        .andExpect(content().string(containsString("code=abc")))
        .andRespond(withSuccess("{\"access_token\":\"issued-token\"}", MediaType.APPLICATION_JSON));

    SocialOauthHttpClient client = new SocialOauthHttpClient(builder.build(), new ObjectMapper());

    LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("grant_type", "authorization_code");
    form.add("code", "abc");

    // when
    String accessToken =
        client.postForm("https://example.com/token", form).path("access_token").asText();

    // then
    assertThat(accessToken).isEqualTo("issued-token");
    server.verify();
  }
}
