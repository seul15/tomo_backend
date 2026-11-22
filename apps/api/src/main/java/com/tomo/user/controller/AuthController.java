package com.tomo.user.controller;

import com.tomo.common.auth.token.AccessTokenClaims;
import com.tomo.common.exception.BusinessException;
import com.tomo.common.exception.CommonErrorCode;
import com.tomo.common.response.CommonResponse;
import com.tomo.user.controller.docs.AuthApiDocs;
import com.tomo.user.dto.response.AuthRefreshResponse;
import com.tomo.user.exception.UserErrorCode;
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

  @Override
  @PostMapping(value = "/refresh", produces = MediaType.APPLICATION_JSON_VALUE)
  public CommonResponse<AuthRefreshResponse> refresh(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
      HttpServletRequest request) {
    String token = requireRefreshBearerToken(authorization);
    return CommonResponse.success(
        AuthRefreshResponse.from(loginSessionFacade.refresh(token)), request.getRequestId());
  }

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
      throw new BusinessException(CommonErrorCode.INVALID_ACCESS_TOKEN);
    }
    loginSessionFacade.logout(userId, claims.loginSessionId());
    return CommonResponse.successWithoutData(request.getRequestId());
  }

  private static String requireRefreshBearerToken(String authorization) {
    if (authorization == null) {
      throw new BusinessException(UserErrorCode.INVALID_REFRESH_TOKEN);
    }
    Matcher matcher = BEARER_PATTERN.matcher(authorization);
    if (!matcher.matches()) {
      throw new BusinessException(UserErrorCode.INVALID_REFRESH_TOKEN);
    }
    return matcher.group(1);
  }
}
