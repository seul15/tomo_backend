package com.tomo.user.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tomo.common.auth.config.SecurityConfig;
import com.tomo.common.auth.token.AccessTokenProvider;
import com.tomo.common.logging.RequestLoggingConfig;
import com.tomo.user.service.LoginSessionFacade;
import com.tomo.user.service.LoginSessionService;
import com.tomo.user.service.model.TokenRefreshResult;
import io.jsonwebtoken.Jwts;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@Import({
  SecurityConfig.class,
  RequestLoggingConfig.class,
  AuthEndpointSecurityTest.JwtTestConfig.class
})
@ActiveProfiles("test")
class AuthEndpointSecurityTest {
  private static final Instant NOW = Instant.parse("2026-11-07T00:00:00Z");
  private static final String REFRESH = "10." + "A".repeat(43);

  @Autowired private MockMvc mockMvc;
  @Autowired private AccessTokenProvider accessTokenProvider;

  @MockitoBean private LoginSessionFacade loginSessionFacade;
  @MockitoBean private LoginSessionService loginSessionService;

  @Test
  @DisplayName("재발급 API는 Access JWT 없이 Refresh Bearer 토큰을 받아 처리한다")
  void refreshUsesOpaqueBearerToken() throws Exception {
    // given
    when(loginSessionFacade.refresh(REFRESH))
        .thenReturn(new TokenRefreshResult("new-access", "new-refresh", 3600, 604800));

    // when
    // then
    mockMvc
        .perform(
            post("/api/v1/auth/refresh").header(HttpHeaders.AUTHORIZATION, "Bearer " + REFRESH))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.accessToken").value("new-access"))
        .andExpect(jsonPath("$.data.refreshToken").value("new-refresh"));
    verify(loginSessionFacade).refresh(REFRESH);
  }

  @Test
  @DisplayName("로그아웃은 Access JWT의 사용자와 세션 ID로 처리하고 data를 생략한다")
  void logoutUsesVerifiedAccessClaims() throws Exception {
    // given
    String access = accessTokenProvider.issue("user-id", 10L, NOW.plusSeconds(600));
    when(loginSessionService.isValidAccessSession("user-id", 10L)).thenReturn(true);

    // when
    // then
    mockMvc
        .perform(post("/api/v1/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").doesNotExist());
    verify(loginSessionFacade).logout("user-id", 10L);
  }

  @Test
  @DisplayName("유효하지 않은 Access JWT로 로그아웃하면 401을 반환한다")
  void rejectsInvalidLogoutJwt() throws Exception {
    // when
    // then
    mockMvc
        .perform(
            post("/api/v1/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("INVALID_ACCESS_TOKEN"));
  }

  @Test
  @DisplayName("서명된 Access JWT의 세션 소유자가 일치하지 않으면 로그아웃은 401을 반환한다")
  void rejectsOtherUserSessionOnLogout() throws Exception {
    // given
    String access = accessTokenProvider.issue("other-user", 10L, NOW.plusSeconds(600));
    when(loginSessionService.isValidAccessSession("other-user", 10L)).thenReturn(false);

    // when
    // then
    mockMvc
        .perform(post("/api/v1/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("INVALID_ACCESS_TOKEN"));
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class JwtTestConfig {
    @Bean
    AccessTokenProvider accessTokenProvider() {
      return new AccessTokenProvider(
          Jwts.SIG.HS256.key().build(), Clock.fixed(NOW, ZoneOffset.UTC));
    }
  }

  @Test
  @DisplayName("이미 로그아웃한 세션의 Access Token으로 재요청하면 401을 반환한다")
  void rejectsRepeatedLogout() throws Exception {
    // given
    String access = accessTokenProvider.issue("user-id", 10L, NOW.plusSeconds(600));

    when(loginSessionService.isValidAccessSession("user-id", 10L)).thenReturn(true, false);

    // when
    // then
    mockMvc
        .perform(post("/api/v1/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    mockMvc
        .perform(post("/api/v1/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("INVALID_ACCESS_TOKEN"));

    verify(loginSessionFacade).logout("user-id", 10L);
  }
}
