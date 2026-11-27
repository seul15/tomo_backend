package com.tomo.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.tomo.common.auth.exception.AuthErrorCode;
import com.tomo.common.auth.token.SignupTokenProvider;
import com.tomo.common.exception.BusinessException;
import com.tomo.common.exception.CommonErrorCode;
import com.tomo.user.client.SocialProviderClient;
import com.tomo.user.entity.AccountStatus;
import com.tomo.user.entity.SocialProvider;
import com.tomo.user.service.SocialAccountService.Account;
import com.tomo.user.service.model.SocialLoginResult;
import com.tomo.user.service.model.TokenRefreshResult;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SocialLoginFacadeTest {
  @Mock private SocialProviderClient google;
  @Mock private SocialAccountService accounts;
  @Mock private SignupTokenProvider signup;
  @Mock private LoginSessionFacade session;
  private SocialLoginFacade facade;

  @BeforeEach
  void prepare() {
    when(google.getProvider()).thenReturn(SocialProvider.GOOGLE);
    facade =
        new SocialLoginFacade(
            List.of(google),
            accounts,
            signup,
            session,
            Duration.ofMinutes(10),
            "https://res.cloudinary.com/tomo/default.png");
  }

  @Test
  @DisplayName("가입 대기 계정에는 Signup Token만 발급하고 로그인 세션은 생성하지 않는다")
  void pendingIssuesSignupOnly() {
    // given
    when(google.authenticateAuthorizationCode("code", "verifier"))
        .thenReturn(new SocialProviderClient.Identity("google-sub"));
    when(accounts.findOrCreate(SocialProvider.GOOGLE, "google-sub"))
        .thenReturn(new Account("user-1", AccountStatus.PENDING, null, null));
    when(signup.issue("user-1")).thenReturn("signup-token");

    // when
    SocialLoginResult result = facade.loginGoogle("code", "verifier");

    // then
    assertThat(result.signupToken()).isEqualTo("signup-token");
    assertThat(result.signupTokenExpiresIn()).isEqualTo(600);
    assertThat(result.tokens()).isNull();
    verifyNoInteractions(session);
  }

  @Test
  @DisplayName("ACTIVE 계정은 기존 세션 발급 기능으로 Access와 Refresh Token을 얻는다")
  void activeCreatesSession() {
    // given
    when(google.authenticateAuthorizationCode("code", "verifier"))
        .thenReturn(new SocialProviderClient.Identity("google-sub"));
    when(accounts.findOrCreate(SocialProvider.GOOGLE, "google-sub"))
        .thenReturn(new Account("user-1", AccountStatus.ACTIVE, "토모", null));
    when(session.issueForActiveUser("user-1"))
        .thenReturn(new TokenRefreshResult("access", "refresh", 3600, 604800));

    // when
    SocialLoginResult result = facade.loginGoogle("code", "verifier");

    // then
    assertThat(result.signupToken()).isNull();
    assertThat(result.tokens().refreshToken()).isEqualTo("refresh");
    assertThat(result.profileImageUrl()).isEqualTo("https://res.cloudinary.com/tomo/default.png");
    verifyNoInteractions(signup);
  }

  @Test
  @DisplayName("BAN 계정에는 어떤 토큰도 발급하지 않는다")
  void bannedCannotLogin() {
    // given
    when(google.authenticateAuthorizationCode("code", "verifier"))
        .thenReturn(new SocialProviderClient.Identity("google-sub"));
    when(accounts.findOrCreate(SocialProvider.GOOGLE, "google-sub"))
        .thenReturn(new Account("user-1", AccountStatus.BAN, null, null));

    // when
    // then
    assertThatThrownBy(() -> facade.loginGoogle("code", "verifier"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex -> assertThat(ex.getErrorCode()).isEqualTo(AuthErrorCode.SOCIAL_LOGIN_FORBIDDEN));
    verifyNoInteractions(signup, session);
  }

  @Test
  @DisplayName("ACTIVE 계정에 기본 프로필 이미지 설정도 없으면 잘못된 성공 응답 대신 500을 반환한다")
  void missingDefaultImageFails() {
    // given
    SocialLoginFacade invalidConfigFacade =
        new SocialLoginFacade(
            List.of(google), accounts, signup, session, Duration.ofMinutes(10), "");
    when(google.authenticateAuthorizationCode("code", "verifier"))
        .thenReturn(new SocialProviderClient.Identity("google-sub"));
    when(accounts.findOrCreate(SocialProvider.GOOGLE, "google-sub"))
        .thenReturn(new Account("user-1", AccountStatus.ACTIVE, "토모", null));

    // when
    // then
    assertThatThrownBy(() -> invalidConfigFacade.loginGoogle("code", "verifier"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex -> assertThat(ex.getErrorCode()).isEqualTo(CommonErrorCode.INTERNAL_SERVER_ERROR));
    verifyNoInteractions(session);
  }
}
