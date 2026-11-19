package com.tomo.user.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tomo.common.auth.config.SecurityConfig;
import com.tomo.common.auth.token.AccessTokenProvider;
import com.tomo.common.exception.BusinessException;
import com.tomo.common.exception.CommonErrorCode;
import com.tomo.user.entity.SocialProvider;
import com.tomo.user.exception.UserErrorCode;
import com.tomo.user.service.LoginSessionService;
import com.tomo.user.service.UserProfileFacade;
import com.tomo.user.service.model.UserProfileResult;
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
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, UserControllerTest.JwtTestConfig.class})
@ActiveProfiles("test")
class UserControllerTest {
  private static final String PATH = "/api/v1/users/me";
  private static final String USER_ID = "user-id";
  private static final long SESSION_ID = 10L;
  private static final Instant NOW = Instant.parse("2026-11-06T00:00:00Z");
  private static final String IMAGE_URL = "https://cdn.example.com/profile/user01.png";

  @Autowired private MockMvc mockMvc;
  @Autowired private AccessTokenProvider accessTokenProvider;

  @MockitoBean private LoginSessionService loginSessionService;
  @MockitoBean private UserProfileFacade userProfileFacade;

  @Test
  @DisplayName("유효한 JWT로 본인의 계정 및 프로필을 조회한다")
  void returnsOwnProfile() throws Exception {
    // given
    String token = accessToken();
    when(loginSessionService.isValidAccessSession(USER_ID, SESSION_ID)).thenReturn(true);
    when(userProfileFacade.getMyProfile(USER_ID))
        .thenReturn(
            new UserProfileResult("집중토모", IMAGE_URL, "test@google.com", SocialProvider.GOOGLE));

    // when
    ResultActions result =
        mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));

    // then
    result
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.nickname").value("집중토모"))
        .andExpect(jsonPath("$.data.profileImageUrl").value(IMAGE_URL))
        .andExpect(jsonPath("$.data.email").value("test@google.com"))
        .andExpect(jsonPath("$.data.provider").value("GOOGLE"))
        .andExpect(jsonPath("$.meta.requestId").exists())
        .andExpect(jsonPath("$.meta.timestamp").isNotEmpty())
        .andExpect(jsonPath("$.data.providerSubject").doesNotExist())
        .andExpect(jsonPath("$.data.accessToken").doesNotExist())
        .andExpect(jsonPath("$.data.refreshToken").doesNotExist());

    verify(userProfileFacade).getMyProfile(USER_ID);
  }

  @Test
  @DisplayName("이메일이 없는 카카오 사용자는 email null과 provider KAKAO를 반환한다")
  void returnsNullEmailForKakaoUser() throws Exception {
    // given
    String token = accessToken();
    when(loginSessionService.isValidAccessSession(USER_ID, SESSION_ID)).thenReturn(true);
    when(userProfileFacade.getMyProfile(USER_ID))
        .thenReturn(new UserProfileResult("집중토모", IMAGE_URL, null, SocialProvider.KAKAO));

    // when
    ResultActions result =
        mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));

    // then
    result
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.email").value(nullValue()))
        .andExpect(jsonPath("$.data.provider").value("KAKAO"));
  }

  @Test
  @DisplayName("Access Token이 없으면 401 INVALID_ACCESS_TOKEN을 반환한다")
  void rejectsMissingToken() throws Exception {
    // when
    ResultActions result = mockMvc.perform(get(PATH));

    // then
    result
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("INVALID_ACCESS_TOKEN"));

    verifyNoInteractions(userProfileFacade);
  }

  @Test
  @DisplayName("잘못된 Access Token이면 401 INVALID_ACCESS_TOKEN을 반환한다")
  void rejectsInvalidToken() throws Exception {
    // when
    ResultActions result =
        mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"));

    // then
    result
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("INVALID_ACCESS_TOKEN"));

    verifyNoInteractions(userProfileFacade);
  }

  @Test
  @DisplayName("JWT가 유효해도 로그인 세션이 유효하지 않으면 401을 반환한다")
  void rejectsInvalidSession() throws Exception {
    // given
    String token = accessToken();
    when(loginSessionService.isValidAccessSession(USER_ID, SESSION_ID)).thenReturn(false);

    // when
    ResultActions result =
        mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));

    // then
    result
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("INVALID_ACCESS_TOKEN"));

    verifyNoInteractions(userProfileFacade);
  }

  @Test
  @DisplayName("조회 중 사용자가 존재하지 않으면 404를 반환한다")
  void returnsNotFound() throws Exception {
    // given
    String token = accessToken();
    when(loginSessionService.isValidAccessSession(USER_ID, SESSION_ID)).thenReturn(true);
    when(userProfileFacade.getMyProfile(USER_ID))
        .thenThrow(new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));

    // when
    ResultActions result =
        mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));

    // then
    result
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
  }

  @Test
  @DisplayName("조회 서비스에서 접근을 거부하면 403을 반환한다")
  void returnsProfileViewForbidden() throws Exception {
    // given
    String token = accessToken();
    when(loginSessionService.isValidAccessSession(USER_ID, SESSION_ID)).thenReturn(true);
    when(userProfileFacade.getMyProfile(USER_ID))
        .thenThrow(new BusinessException(UserErrorCode.PROFILE_VIEW_FORBIDDEN));

    // when
    ResultActions result =
        mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));

    // then
    result
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("PROFILE_VIEW_FORBIDDEN"));
  }

  private String accessToken() {
    return accessTokenProvider.issue(USER_ID, SESSION_ID, NOW.plusSeconds(600));
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
