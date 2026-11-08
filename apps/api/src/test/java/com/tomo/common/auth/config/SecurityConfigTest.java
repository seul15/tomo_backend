package com.tomo.common.auth.config;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tomo.common.auth.token.AccessTokenProvider;
import com.tomo.user.service.LoginSessionService;
import io.jsonwebtoken.Jwts;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(SecurityConfigTest.TestController.class)
@Import({SecurityConfig.class, SecurityConfigTest.TestController.class})
@ActiveProfiles("test")
class SecurityConfigTest {
  private static final Instant NOW = Instant.parse("2026-11-06T00:00:00Z");

  @Autowired private MockMvc mockMvc;
  @Autowired private AccessTokenProvider accessTokenProvider;

  @MockitoBean private LoginSessionService loginSessionService;

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
        .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
  }

  @RestController
  static class TestController {

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
