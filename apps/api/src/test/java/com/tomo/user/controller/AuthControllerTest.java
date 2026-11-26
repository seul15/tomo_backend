package com.tomo.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tomo.common.auth.exception.AuthErrorCode;
import com.tomo.common.auth.token.AccessTokenClaims;
import com.tomo.common.exception.BusinessException;
import com.tomo.common.response.CommonResponse;
import com.tomo.user.dto.request.GoogleLoginRequest;
import com.tomo.user.dto.request.KakaoLoginRequest;
import com.tomo.user.dto.response.AuthRefreshResponse;
import com.tomo.user.dto.response.SocialLoginResponse;
import com.tomo.user.service.LoginSessionFacade;
import com.tomo.user.service.SocialLoginFacade;
import com.tomo.user.service.model.SocialLoginResult;
import com.tomo.user.service.model.TokenRefreshResult;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {
  private static final String REFRESH = "10." + "A".repeat(43);

  @Mock private LoginSessionFacade loginSessionFacade;
  @Mock private HttpServletRequest request;
  @Mock private SocialLoginFacade socialLoginFacade;
  @InjectMocks private AuthController controller;

  @Test
  @DisplayName("Refresh Bearer 헤더를 전달하면 재발급 결과를 공통 응답으로 반환한다")
  void refreshesToken() {
    // given
    when(request.getRequestId()).thenReturn("request-1");
    when(loginSessionFacade.refresh(REFRESH))
        .thenReturn(new TokenRefreshResult("new-access", "new-refresh", 3600, 604800));

    // when
    CommonResponse<AuthRefreshResponse> response = controller.refresh("Bearer " + REFRESH, request);

    // then
    assertThat(response.success()).isTrue();
    assertThat(response.data().accessToken()).isEqualTo("new-access");
    assertThat(response.data().refreshToken()).isEqualTo("new-refresh");
    assertThat(response.data().user().role()).isEqualTo("USER");
    verify(loginSessionFacade).refresh(REFRESH);
  }

  @Test
  @DisplayName("잘못된 Refresh Bearer 헤더는 서비스 호출 전에 401 업무 예외로 거부한다")
  void rejectsInvalidAuthorization() {
    // when
    // then
    assertThatThrownBy(() -> controller.refresh("Basic invalid", request))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN));
    verifyNoInteractions(loginSessionFacade);
  }

  @Test
  @DisplayName("Google 로그인에서 PKCE 형식 오류는 외부 인증 호출 전에 400으로 처리한다")
  void rejectsShortGoogleVerifier() {
    // when
    // then
    assertThatThrownBy(() -> controller.google(new GoogleLoginRequest("code", "short"), request))
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex ->
                assertThat(ex.getErrorCode())
                    .isEqualTo(com.tomo.common.exception.CommonErrorCode.INVALID_REQUEST));
    verifyNoInteractions(socialLoginFacade);
  }

  @Test
  @DisplayName("Google 신규 회원은 응답에 Signup Token만 포함한다")
  void googlePendingResponse() {
    // given
    String verifier = "A".repeat(43);
    when(socialLoginFacade.loginGoogle("code", verifier))
        .thenReturn(SocialLoginResult.pending("signup-jwt", 600));
    when(request.getRequestId()).thenReturn("req-1");

    // when
    CommonResponse<SocialLoginResponse> response =
        controller.google(new GoogleLoginRequest("code", verifier), request);

    // then
    assertThat(response.data())
        .isInstanceOf(com.tomo.user.dto.response.SignupPendingResponse.class);
    com.tomo.user.dto.response.SignupPendingResponse data =
        (com.tomo.user.dto.response.SignupPendingResponse) response.data();
    assertThat(data.signupToken()).isEqualTo("signup-jwt");
  }

  @Test
  @DisplayName("Kakao ACTIVE 회원은 Access와 Refresh Token을 응답한다")
  void kakaoActiveResponse() {
    // given
    when(socialLoginFacade.loginKakao("code"))
        .thenReturn(
            SocialLoginResult.active(
                new TokenRefreshResult("access", "refresh", 3600, 604800),
                "토모",
                "https://example.com/default.png"));
    when(request.getRequestId()).thenReturn("req-2");

    // when
    CommonResponse<SocialLoginResponse> response =
        controller.kakao(new KakaoLoginRequest("code"), request);

    // then
    assertThat(response.data())
        .isInstanceOf(com.tomo.user.dto.response.AuthenticatedUserResponse.class);
    com.tomo.user.dto.response.AuthenticatedUserResponse data =
        (com.tomo.user.dto.response.AuthenticatedUserResponse) response.data();
    assertThat(data.accessToken()).isEqualTo("access");
    assertThat(data.refreshToken()).isEqualTo("refresh");
  }

  @Test
  @DisplayName("로그아웃은 Access Token의 세션 ID로 현재 세션을 폐기한다")
  void logsOutCurrentSession() throws Exception {
    // given
    when(request.getRequestId()).thenReturn("request-2");
    UsernamePasswordAuthenticationToken authentication =
        new UsernamePasswordAuthenticationToken("user-id", null);
    authentication.setDetails(new AccessTokenClaims("user-id", 10L));

    // when
    CommonResponse<Void> response = controller.logout("user-id", authentication, request);

    // then
    assertThat(response.success()).isTrue();
    assertThat(response.data()).isNull();
    verify(loginSessionFacade).logout("user-id", 10L);
    ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    String json = mapper.writeValueAsString(response);
    assertThat(mapper.readTree(json).has("data")).isFalse();
  }

  @Test
  @DisplayName("로그아웃의 검증된 세션 정보가 없으면 접근을 거부한다")
  void rejectsLogoutWithoutSessionClaims() {
    // given
    UsernamePasswordAuthenticationToken authentication =
        new UsernamePasswordAuthenticationToken("user-id", null);

    // when
    // then
    assertThatThrownBy(() -> controller.logout("user-id", authentication, request))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_ACCESS_TOKEN));
    verifyNoInteractions(loginSessionFacade);
  }
}
