package com.tomo.common.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tomo.common.auth.token.AccessTokenProvider;
import com.tomo.common.logging.RequestIdFilter;
import com.tomo.common.logging.RequestLoggingConfig;
import com.tomo.common.response.CommonResponse;
import com.tomo.user.service.LoginSessionService;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

@WebMvcTest(SecurityConfigTest.TestController.class)
@Import({SecurityConfig.class, SecurityConfigTest.TestController.class, RequestLoggingConfig.class})
@ActiveProfiles("test")
class SecurityConfigTest {
  private static final Instant NOW = Instant.parse("2026-11-06T00:00:00Z");

  @Autowired private MockMvc mockMvc;
  @Autowired private AccessTokenProvider accessTokenProvider;
  @Autowired private AccessDeniedHandler accessDeniedHandler;
  @Autowired private WebApplicationContext applicationContext;
  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private LoginSessionService loginSessionService;

  @ParameterizedTest
  @ValueSource(ints = {200, 401, 403})
  @ExtendWith(OutputCaptureExtension.class)
  @DisplayName("실제 정상·인증 실패·권한 거부 응답의 메타데이터와 로그 요청 ID가 일치한다")
  void matchesResponseMetadataWithLogs(int expectedStatus, CapturedOutput output) throws Exception {
    // given
    Filter logObserver =
        (request, response, chain) -> {
          chain.doFilter(request, response);
          LoggerFactory.getLogger(SecurityConfigTest.class)
              .info("응답 경로 검증. status={}", ((HttpServletResponse) response).getStatus());
        };
    MockMvc requestIdMockMvc =
        MockMvcBuilders.webAppContextSetup(applicationContext)
            .addFilters(new RequestIdFilter(), logObserver)
            .apply(springSecurity())
            .build();
    String token = accessTokenProvider.issue("user-id", 10L, NOW.plusSeconds(600));
    if (expectedStatus != 401) {
      when(loginSessionService.isValidAccessSession("user-id", 10L)).thenReturn(true);
    }
    String path = expectedStatus == 403 ? "/api/denied" : "/api/request-id";
    MockHttpServletRequestBuilder requestBuilder =
        get(path)
            .with(
                request -> {
                  MockHttpServletRequest identifiedRequest = spy(request);
                  when(identifiedRequest.getRequestId()).thenReturn("response-" + expectedStatus);
                  return identifiedRequest;
                });
    if (expectedStatus != 401) {
      requestBuilder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    // when
    MvcResult result = requestIdMockMvc.perform(requestBuilder).andReturn();

    // then
    assertThat(result.getResponse().getStatus()).isEqualTo(expectedStatus);
    String responseRequestId =
        objectMapper
            .readTree(result.getResponse().getContentAsString())
            .path("meta")
            .path("requestId")
            .asText();
    assertThat(responseRequestId).isEqualTo("response-" + expectedStatus);
    assertThat(output.getOut())
        .contains("[requestId=" + responseRequestId + "] 응답 경로 검증. status=" + expectedStatus);
    assertThat(MDC.get("requestId")).isNull();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/scalar",
        "/scalar/",
        "/scalar/index.html",
        "/v3/api-docs",
        "/v3/api-docs/swagger-config",
        "/v3/api-docs.yaml"
      })
  @DisplayName("API 문서 GET 요청은 인증 없이 접근할 수 있다")
  void allowsDocumentation(String path) throws Exception {
    // when
    ResultActions result = mockMvc.perform(get(path));

    // then
    result.andExpect(status().isOk());
  }

  @Test
  @DisplayName("문서 이외의 경로는 비인증 요청에 로그인 화면 대신 401을 반환한다")
  void requiresAuthentication() throws Exception {
    // when
    ResultActions result = mockMvc.perform(get("/api/private"));

    // then
    result.andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("유효한 Access Token과 세션으로 현재 사용자 ID를 조회한다")
  void authenticatesValidToken() throws Exception {
    // given
    String token = accessTokenProvider.issue("user-id", 10L, NOW.plusSeconds(600));
    when(loginSessionService.isValidAccessSession("user-id", 10L)).thenReturn(true);

    // when
    ResultActions result =
        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));

    // then
    result.andExpect(status().isOk()).andExpect(content().string("user-id"));
  }

  @Test
  @DisplayName("잘못된 Access Token은 인증하지 않는다")
  void rejectsInvalidToken() throws Exception {
    // when
    ResultActions result =
        mockMvc.perform(
            get("/api/private").header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"));

    // then
    result
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("INVALID_ACCESS_TOKEN"));
  }

  @Test
  @DisplayName("JWT 서명이 유효해도 로그인 세션이 유효하지 않으면 거부한다")
  void rejectsInvalidSession() throws Exception {
    // given
    String token = accessTokenProvider.issue("user-id", 10L, NOW.plusSeconds(600));
    when(loginSessionService.isValidAccessSession("user-id", 10L)).thenReturn(false);

    // when
    ResultActions result =
        mockMvc.perform(get("/api/private").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));

    // then
    result
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("INVALID_ACCESS_TOKEN"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"bearer", "BEARER", "Bearer"})
  @DisplayName("Bearer 인증 스킴의 대소문자에 관계없이 유효한 토큰을 인증한다")
  void authenticatesBearerSchemeIgnoringCase(String scheme) throws Exception {
    // given
    String token = accessTokenProvider.issue("user-id", 10L, NOW.plusSeconds(600));
    when(loginSessionService.isValidAccessSession("user-id", 10L)).thenReturn(true);

    // when
    ResultActions result =
        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, scheme + "   " + token));

    // then
    result.andExpect(status().isOk()).andExpect(content().string("user-id"));
  }

  @Test
  @ExtendWith(OutputCaptureExtension.class)
  @DisplayName("로그인 세션 검증 중 서버 오류가 발생하면 공통 500 응답을 반환한다")
  void handlesSessionValidationFailure(CapturedOutput output) throws Exception {
    // given
    String token = accessTokenProvider.issue("user-id", 10L, NOW.plusSeconds(600));

    when(loginSessionService.isValidAccessSession("user-id", 10L))
        .thenThrow(new IllegalStateException("DB 연결 불가"));

    // when
    ResultActions result =
        mockMvc.perform(
            get("/api/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .with(
                    request -> {
                      MockHttpServletRequest identifiedRequest = spy(request);
                      when(identifiedRequest.getRequestId()).thenReturn("session-failure");
                      return identifiedRequest;
                    }));

    // then
    result
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.error.code").value("INTERNAL_SERVER_ERROR"))
        .andExpect(jsonPath("$.meta.requestId").value("session-failure"));

    assertThat(output.getOut()).contains("[requestId=session-failure] 로그인 세션 검증 중 서버 오류");

    assertThat(MDC.get("requestId")).isNull();
  }

  @Test
  @DisplayName("유효한 JWT가 있으면 CSRF 토큰 없이 POST 요청할 수 있다")
  void allowsAuthenticatedPostWithoutCsrfToken() throws Exception {
    // given
    String token = accessTokenProvider.issue("user-id", 10L, NOW.plusSeconds(600));

    when(loginSessionService.isValidAccessSession("user-id", 10L)).thenReturn(true);

    // when
    ResultActions result =
        mockMvc.perform(post("/api/private").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));

    // then
    result.andExpect(status().isOk()).andExpect(content().string("ok"));
  }

  @Test
  @DisplayName("JWT가 없는 POST 요청은 401을 반환한다")
  void rejectsUnauthenticatedPost() throws Exception {
    // when
    ResultActions result = mockMvc.perform(post("/api/private"));

    // then
    result
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("INVALID_ACCESS_TOKEN"));
  }

  @Test
  @DisplayName("권한 부족 요청에 공통 403 오류 응답을 반환한다")
  void rejectsAccessDenied() throws Exception {
    // given
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();

    // when
    accessDeniedHandler.handle(request, response, new AccessDeniedException("접근 권한 없음"));

    // then
    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(response.getContentAsString()).contains("\"code\":\"FORBIDDEN\"");
  }

  @Test
  @DisplayName("인증된 요청에서 권한 부족 예외가 발생하면 403을 반환한다")
  void handlesMvcAccessDenied() throws Exception {
    // given
    String token = accessTokenProvider.issue("user-id", 10L, NOW.plusSeconds(600));
    when(loginSessionService.isValidAccessSession("user-id", 10L)).thenReturn(true);

    // when
    ResultActions result =
        mockMvc.perform(get("/api/denied").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));

    // then
    result.andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/api/v1/auth/google",
        "/api/v1/auth/kakao",
        "/api/v1/auth/refresh",
        "/api/v1/users/me/profile"
      })
  @DisplayName("소셜·가입·재발급 POST 경로는 Access JWT 필터 없이 진입할 수 있다")
  void permitsPublicAuthPosts(String path) throws Exception {
    // when
    ResultActions result =
        mockMvc.perform(post(path).header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"));

    // then
    result.andExpect(status().isOk()).andExpect(content().string("ok"));
  }

  @Test
  @DisplayName("닉네임 확인 GET 경로는 Access JWT 필터 없이 진입할 수 있다")
  void permitsNicknameGet() throws Exception {
    // when
    ResultActions result =
        mockMvc.perform(
            get("/api/v1/auth/nickname").header(HttpHeaders.AUTHORIZATION, "Bearer signup-token"));

    // then
    result.andExpect(status().isOk()).andExpect(content().string("ok"));
  }

  @RestController
  static class TestController {

    @GetMapping("/api/request-id")
    CommonResponse<String> requestIdResponse(HttpServletRequest request) {
      return CommonResponse.success("ok", request.getRequestId());
    }

    @GetMapping({
      "/scalar",
      "/scalar/**",
      "/v3/api-docs",
      "/v3/api-docs/**",
      "/v3/api-docs.yaml",
      "/api/private"
    })
    String endpoint() {
      return "ok";
    }

    @GetMapping("/api/me")
    String currentUser(@AuthenticationPrincipal String userId) {
      return userId;
    }

    @PostMapping("/api/private")
    String privatePost() {
      return "ok";
    }

    @GetMapping("/api/denied")
    String denied() {
      throw new AccessDeniedException("접근 권한 없음");
    }

    @PostMapping({
      "/api/v1/auth/google",
      "/api/v1/auth/kakao",
      "/api/v1/auth/refresh",
      "/api/v1/users/me/profile"
    })
    String publicAuthPost() {
      return "ok";
    }

    @GetMapping("/api/v1/auth/nickname")
    String publicNicknameGet() {
      return "ok";
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class JwtTestConfig {

    @Bean
    AccessTokenProvider accessTokenProvider() {
      return new AccessTokenProvider(
          Jwts.SIG.HS256.key().build(), Clock.fixed(NOW, ZoneOffset.UTC));
    }
  }
}
