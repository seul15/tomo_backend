package com.tomo.user.controller;

import com.tomo.common.auth.exception.AuthErrorCode;
import com.tomo.common.auth.token.AccessTokenClaims;
import com.tomo.common.exception.BusinessException;
import com.tomo.common.response.CommonResponse;
import com.tomo.user.controller.docs.AuthApiDocs;
import com.tomo.user.dto.response.AuthRefreshResponse;
import com.tomo.user.service.LoginSessionFacade;
import jakarta.servlet.http.HttpServletRequest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 인증 토큰 재발급과 현재 기기 로그아웃 API를 제공한다. */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController implements AuthApiDocs {
  private static final Pattern BEARER_PATTERN =
      Pattern.compile("(?i)^Bearer[ \t]+([^ \t]+)[ \t]*$");

  private final LoginSessionFacade loginSessionFacade;

  /**
   * 인증 세션 업무 조정 객체를 주입한다.
   *
   * @param loginSessionFacade 로그인 세션과 Refresh Token 업무 조정 객체
   */
  public AuthController(LoginSessionFacade loginSessionFacade) {
    this.loginSessionFacade = loginSessionFacade;
  }

  /**
   * Refresh Token을 검증하고 새 인증 토큰 쌍을 반환한다.
   *
   * @param authorization Bearer 형식의 Refresh Token 요청 헤더
   * @param request 요청 ID 확인용 HTTP 요청
   * @return 새 Access Token과 Refresh Token을 포함한 공통 응답
   * @throws BusinessException Refresh Token이 없거나 형식이 올바르지 않은 경우
   */
  @Override
  @PostMapping(value = "/refresh", produces = MediaType.APPLICATION_JSON_VALUE)
  public CommonResponse<AuthRefreshResponse> refresh(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
      HttpServletRequest request) {
    String token = requireRefreshBearerToken(authorization);
    return CommonResponse.success(
        AuthRefreshResponse.from(loginSessionFacade.refresh(token)), request.getRequestId());
  }

  /**
   * 인증된 사용자의 현재 로그인 세션을 종료한다.
   *
   * @param userId Access Token에서 인증된 사용자 ID
   * @param authentication 검증된 Access Token의 로그인 세션 정보
   * @param request 요청 ID 확인용 HTTP 요청
   * @return 데이터 필드가 없는 로그아웃 성공 응답
   * @throws BusinessException 인증 정보나 로그인 세션 정보가 유효하지 않은 경우
   */
  @Override
  @PostMapping(value = "/logout", produces = MediaType.APPLICATION_JSON_VALUE)
  public CommonResponse<Void> logout(
      @AuthenticationPrincipal String userId,
      Authentication authentication,
      HttpServletRequest request) {
    if (authentication == null
        || !(authentication.getDetails() instanceof AccessTokenClaims claims)
        || userId == null
        || !userId.equals(claims.userId())) {
      throw new BusinessException(AuthErrorCode.INVALID_ACCESS_TOKEN);
    }
    loginSessionFacade.logout(userId, claims.loginSessionId());
    return CommonResponse.successWithoutData(request.getRequestId());
  }

  /**
   * Authorization 헤더에서 Bearer Refresh Token 원문을 추출한다.
   *
   * @param authorization Authorization 요청 헤더
   * @return Bearer 접두사를 제거한 Refresh Token
   * @throws BusinessException 헤더가 없거나 Bearer 형식이 잘못된 경우
   */
  private static String requireRefreshBearerToken(String authorization) {
    if (authorization == null) {
      throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
    }
    Matcher matcher = BEARER_PATTERN.matcher(authorization);
    if (!matcher.matches()) {
      throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
    }
    return matcher.group(1);
  }
}
