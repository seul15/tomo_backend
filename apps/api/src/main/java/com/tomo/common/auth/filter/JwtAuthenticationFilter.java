package com.tomo.common.auth.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.tomo.common.auth.exception.AuthErrorCode;
import com.tomo.common.auth.session.AccessSessionValidator;
import com.tomo.common.auth.token.AccessTokenClaims;
import com.tomo.common.auth.token.AccessTokenProvider;
import com.tomo.common.exception.BusinessException;
import com.tomo.common.exception.CommonErrorCode;
import com.tomo.common.response.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** 요청의 Access Token과 로그인 세션을 검증해 인증 사용자를 등록한다. */
public class JwtAuthenticationFilter extends OncePerRequestFilter {
  private static final Logger LOG = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

  private static final Pattern BEARER_TOKEN_PATTERN =
      Pattern.compile("(?i)^Bearer[ \\t]+([^ \\t]+)[ \\t]*$");

  private final AccessTokenProvider accessTokenProvider;
  private final ObjectWriter objectWriter;
  private final AccessSessionValidator accessSessionValidator;

  /**
   * Access 인증에 필요한 의존성을 주입한다.
   *
   * @param accessTokenProvider Access Token 검증기
   * @param accessSessionValidator 로그인 세션 유효성 검증 계약
   * @param objectMapper 공통 오류 응답 직렬화 객체
   */
  public JwtAuthenticationFilter(
      AccessTokenProvider accessTokenProvider,
      AccessSessionValidator accessSessionValidator,
      ObjectMapper objectMapper) {
    this.accessTokenProvider = accessTokenProvider;
    this.accessSessionValidator = accessSessionValidator;
    this.objectWriter = objectMapper.writer();
  }

  /**
   * Access JWT 검증을 적용하지 않을 공개 API 요청인지 판단한다.
   *
   * <p>요청 URI에서 컨텍스트 경로를 제외한 뒤 HTTP 메서드와 API 경로를 함께 비교한다. 재발급 요청은 별도의 Refresh Token 검증 절차를 수행한다.
   *
   * @param request HTTP 요청
   * @return Access JWT 필터를 건너뛸 요청이면 true
   */
  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String requestUri = request.getRequestURI();
    String contextPath = request.getContextPath();
    String path = requestUri.substring(contextPath.length());
    String method = request.getMethod();

    if ("POST".equals(method)) {
      return "/api/v1/auth/google".equals(path)
          || "/api/v1/auth/kakao".equals(path)
          || "/api/v1/auth/refresh".equals(path)
          || "/api/v1/users/me/profile".equals(path);
    }

    return "GET".equals(method) && "/api/v1/auth/nickname".equals(path);
  }

  /**
   * HTTP 요청의 Access Token과 로그인 세션을 검증하여 인증 정보를 등록한다.
   *
   * <p>Authorization 헤더가 없으면 후속 보안 필터에 인증 판단을 위임한다. 토큰 또는 세션 검증에 실패하면 공통 401 오류 응답을 반환한다.
   *
   * @param request HTTP 요청
   * @param response HTTP 응답
   * @param filterChain 후속 필터 체인
   * @throws ServletException 후속 필터 처리에서 서블릿 오류가 발생한 경우
   * @throws IOException HTTP 응답 또는 후속 필터 처리에서 입출력 오류가 발생한 경우
   */
  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);

    if (authorization == null) {
      filterChain.doFilter(request, response);
      return;
    }

    Matcher matcher = BEARER_TOKEN_PATTERN.matcher(authorization);

    if (!matcher.matches()) {
      reject(request, response, "INVALID_BEARER_FORMAT");
      return;
    }

    AccessTokenClaims claims;

    try {
      claims = accessTokenProvider.verify(matcher.group(1));
    } catch (BusinessException exception) {
      reject(request, response, "TOKEN_VERIFICATION_FAILED");
      return;
    }

    boolean validSession;

    try {
      validSession =
          accessSessionValidator.isValidAccessSession(claims.userId(), claims.loginSessionId());
    } catch (RuntimeException exception) {
      SecurityContextHolder.clearContext();

      LOG.error("로그인 세션 검증 중 서버 오류", exception);

      response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      response.setCharacterEncoding(StandardCharsets.UTF_8.name());

      objectWriter.writeValue(
          response.getOutputStream(),
          ErrorResponse.from(CommonErrorCode.INTERNAL_SERVER_ERROR, request.getRequestId()));
      return;
    }

    if (!validSession) {
      reject(request, response, "INVALID_LOGIN_SESSION");
      return;
    }

    UsernamePasswordAuthenticationToken authentication =
        new UsernamePasswordAuthenticationToken(claims.userId(), null, List.of());

    authentication.setDetails(claims);
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(authentication);
    SecurityContextHolder.setContext(context);

    filterChain.doFilter(request, response);
  }

  /**
   * Access Token 인증 실패 시 공통 401 오류 응답을 작성한다.
   *
   * @param request HTTP 요청
   * @param response HTTP 응답
   * @param reason 내부 진단용 인증 거부 사유
   * @throws IOException 오류 응답 직렬화에 실패한 경우
   */
  private void reject(HttpServletRequest request, HttpServletResponse response, String reason)
      throws IOException {

    SecurityContextHolder.clearContext();

    LOG.debug("Access Token 인증 거부. reason={}", reason);

    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());

    objectWriter.writeValue(
        response.getOutputStream(),
        ErrorResponse.from(AuthErrorCode.INVALID_ACCESS_TOKEN, request.getRequestId()));
  }
}
