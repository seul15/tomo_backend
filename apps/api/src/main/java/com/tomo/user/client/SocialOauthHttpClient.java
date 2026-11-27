package com.tomo.user.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.tomo.common.auth.exception.AuthErrorCode;
import com.tomo.common.exception.BusinessException;
import com.tomo.common.exception.CommonErrorCode;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** 소셜 제공자에 토큰 교환 및 사용자 정보 조회 HTTP 요청을 보낸다. */
@Component
public class SocialOauthHttpClient {
  private static final Logger LOG = LoggerFactory.getLogger(SocialOauthHttpClient.class);
  private final RestClient restClient;
  private final ObjectReader errorReader;

  /**
   * Spring이 구성한 HTTP·JSON 객체를 이용해 OAuth 클라이언트를 생성한다.
   *
   * @param builder Spring RestClient Builder
   * @param objectMapper 공통 JSON 매퍼
   */
  @Autowired
  public SocialOauthHttpClient(RestClient.Builder builder, ObjectMapper objectMapper) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();

    factory.setConnectTimeout(Duration.ofSeconds(3));
    factory.setReadTimeout(Duration.ofSeconds(5));

    this.restClient = builder.requestFactory(factory).build();
    this.errorReader = objectMapper.reader();
  }

  /**
   * HTTP 응답을 직접 제어하는 테스트에서 사용한다.
   *
   * @param restClient 테스트용 HTTP 클라이언트
   * @param objectMapper 테스트용 JSON 매퍼
   */
  SocialOauthHttpClient(RestClient restClient, ObjectMapper objectMapper) {
    this.restClient = restClient;
    this.errorReader = objectMapper.reader();
  }

  /**
   * 인가 코드를 제공자 Access Token으로 교환한다.
   *
   * @param endpoint 고정된 제공자 토큰 URL
   * @param form 토큰 교환 요청 필드
   * @return JSON 토큰 응답
   * @throws BusinessException 인가 코드 오류 또는 제공자 통신 장애
   */
  public JsonNode postForm(String endpoint, MultiValueMap<String, String> form) {
    try {
      return requireObject(
          restClient
              .post()
              .uri(endpoint)
              .contentType(MediaType.APPLICATION_FORM_URLENCODED)
              .body(form)
              .retrieve()
              .body(JsonNode.class));
    } catch (RestClientException exception) {
      throw translateFailure(exception, true);
    }
  }

  /**
   * 제공자 Access Token으로 사용자 정보를 조회한다.
   *
   * @param endpoint 고정된 제공자 사용자 정보 URL
   * @param accessToken 제공자 Access Token
   * @return JSON 사용자 정보
   * @throws BusinessException 제공자 인증 또는 통신 장애
   */
  public JsonNode getProfile(String endpoint, String accessToken) {
    try {
      return requireObject(
          restClient
              .get()
              .uri(endpoint)
              .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
              .retrieve()
              .body(JsonNode.class));
    } catch (RestClientException exception) {
      throw translateFailure(exception, false);
    }
  }

  private static JsonNode requireObject(JsonNode body) {
    if (body == null || !body.isObject()) {
      throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
    }
    return body;
  }

  /**
   * OAuth 제공자의 HTTP 오류를 기존 인증·공통 오류 코드로 변환한다.
   *
   * <p>인가 코드 오류(invalid_grant)와 사용자 정보 조회 인증 거부는 클라이언트 인증 실패로 처리한다. 제공자 설정 오류, 요청 제한 및 통신 장애는 서버 내부
   * 오류로 처리한다.
   *
   * @param exception OAuth HTTP 요청 중 발생한 예외
   * @param tokenRequest 토큰 교환 요청이면 true, 사용자 정보 조회면 false
   * @return 공통 예외 처리기에 전달할 업무 예외
   */
  private BusinessException translateFailure(RestClientException exception, boolean tokenRequest) {

    if (exception instanceof RestClientResponseException response) {
      int status = response.getStatusCode().value();

      if (tokenRequest
          && (status == 400 || status == 401)
          && "invalid_grant".equals(readOAuthError(response))) {
        LOG.debug("소셜 인증 거부. status={}", status);
        return new BusinessException(AuthErrorCode.OAUTH_AUTHENTICATION_FAILED);
      }

      if (!tokenRequest && (status == 401 || status == 403)) {
        LOG.debug("소셜 인증 거부. status={}", status);
        return new BusinessException(AuthErrorCode.OAUTH_AUTHENTICATION_FAILED);
      }
    }

    return new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
  }

  private String readOAuthError(RestClientResponseException response) {
    try {
      JsonNode body = errorReader.readTree(response.getResponseBodyAsByteArray());
      return body == null ? "" : body.path("error").asText("");
    } catch (java.io.IOException exception) {
      return "";
    }
  }
}
