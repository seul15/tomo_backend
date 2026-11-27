package com.tomo.user.service;

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
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** 소셜 제공자 인증, 계정 상태 분기 및 기존 토큰 발급 기능을 조정한다. */
@Service
public final class SocialLoginFacade {
  private static final Logger LOG = LoggerFactory.getLogger(SocialLoginFacade.class);

  private final Map<SocialProvider, SocialProviderClient> clients;
  private final SocialAccountService socialAccountService;
  private final SignupTokenProvider signupTokenProvider;
  private final LoginSessionFacade loginSessionFacade;
  private final Duration signupTokenTtl;
  private final String defaultImageUrl;

  /**
   * 제공자별 클라이언트 및 인증 업무 서비스를 주입한다.
   *
   * @param providerClients Google·Kakao 클라이언트
   * @param socialAccountService 소셜 계정 조회·생성 서비스
   * @param signupTokenProvider 가입 전용 JWT 발급기
   * @param loginSessionFacade 기존 로그인 세션 발급 Facade
   * @param signupTokenTtl 가입 토큰 TTL
   * @param defaultImageUrl 기본 프로필 이미지 URL
   * @throws IllegalStateException 동일한 소셜 제공자 클라이언트가 중복 등록된 경우
   */
  public SocialLoginFacade(
      List<SocialProviderClient> providerClients,
      SocialAccountService socialAccountService,
      SignupTokenProvider signupTokenProvider,
      LoginSessionFacade loginSessionFacade,
      @Value("${tomo.auth.signup-token-expiration}") Duration signupTokenTtl,
      @Value("${tomo.profile.default-image-url:}") String defaultImageUrl) {
    this.clients = new EnumMap<>(SocialProvider.class);
    for (SocialProviderClient client : providerClients) {
      if (clients.putIfAbsent(client.getProvider(), client) != null) {
        throw new IllegalStateException("중복 소셜 인증 제공자: " + client.getProvider());
      }
    }
    this.socialAccountService = socialAccountService;
    this.signupTokenProvider = signupTokenProvider;
    this.loginSessionFacade = loginSessionFacade;
    this.signupTokenTtl = signupTokenTtl;
    this.defaultImageUrl = defaultImageUrl;
  }

  /**
   * Google Authorization Code와 PKCE 검증값으로 로그인한다.
   *
   * @param authorizationCode Google 인가 코드
   * @param codeVerifier Google PKCE 검증값
   * @return 가입 대기 또는 로그인 완료 결과
   */
  public SocialLoginResult loginGoogle(String authorizationCode, String codeVerifier) {
    return login(SocialProvider.GOOGLE, authorizationCode, codeVerifier);
  }

  /**
   * Kakao Authorization Code로 로그인한다.
   *
   * @param authorizationCode Kakao 인가 코드
   * @return 가입 대기 또는 로그인 완료 결과
   */
  public SocialLoginResult loginKakao(String authorizationCode) {
    return login(SocialProvider.KAKAO, authorizationCode, null);
  }

  /**
   * 소셜 제공자의 인증 결과를 회원 상태에 따라 처리한다.
   *
   * <p>PENDING 회원에게는 가입 전용 토큰만 발급하고, ACTIVE 회원에게는 기존 로그인 세션 발급 기능을 사용한다. BAN 회원은 로그인을 거부한다.
   *
   * <p>외부 OAuth 인증을 먼저 완료한 뒤 회원 정보를 조회하므로 외부 통신이 회원 DB 트랜잭션에 포함되지 않는다.
   *
   * @param provider 인증 제공자
   * @param authorizationCode 제공자 인가 코드
   * @param codeVerifier Google PKCE 검증값. Kakao에서는 null
   * @return 가입 대기 또는 로그인 완료 결과
   * @throws BusinessException 인증 결과나 계정 상태가 유효하지 않은 경우
   */
  private SocialLoginResult login(
      SocialProvider provider, String authorizationCode, String codeVerifier) {
    SocialProviderClient client = clients.get(provider);
    if (client == null) {
      LOG.warn("소셜 로그인 실패. 제공자 클라이언트가 등록되지 않음. provider={}", provider);
      throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
    }

    SocialProviderClient.Identity identity =
        client.authenticateAuthorizationCode(authorizationCode, codeVerifier);

    if (identity == null
        || identity.providerSubject() == null
        || identity.providerSubject().isBlank()) {
      LOG.warn("소셜 로그인 실패. 제공자 사용자 식별 정보가 유효하지 않음. provider={}", provider);
      throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
    }

    Account account = socialAccountService.findOrCreate(provider, identity.providerSubject());
    if (account.status() == AccountStatus.PENDING) {
      return SocialLoginResult.pending(
          signupTokenProvider.issue(account.userId()), signupTokenTtl.toSeconds());
    }
    if (account.status() != AccountStatus.ACTIVE) {
      throw new BusinessException(AuthErrorCode.SOCIAL_LOGIN_FORBIDDEN);
    }

    String imageUrl = account.profileImageUrl();

    if (imageUrl == null || imageUrl.isBlank()) {
      imageUrl = defaultImageUrl;
    }

    if (account.nickname() == null || account.nickname().isBlank()) {
      LOG.warn("소셜 로그인 실패. 가입 완료 회원의 닉네임이 없음. provider={}", provider);
      throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
    }

    if (imageUrl == null || imageUrl.isBlank()) {
      LOG.warn("소셜 로그인 실패. 등록된 프로필 이미지와 기본 이미지 설정이 모두 없음. provider={}", provider);
      throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
    }

    TokenRefreshResult tokens = loginSessionFacade.issueForActiveUser(account.userId());
    return SocialLoginResult.active(tokens, account.nickname(), imageUrl);
  }
}
