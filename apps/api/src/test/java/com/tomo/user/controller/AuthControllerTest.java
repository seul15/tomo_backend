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
import com.tomo.user.dto.response.AuthRefreshResponse;
import com.tomo.user.service.LoginSessionFacade;
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
