package com.tomo.common.auth.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.tomo.common.auth.token.AccessTokenClaims;
import com.tomo.common.auth.token.AccessTokenProvider;
import com.tomo.common.exception.BusinessException;
import com.tomo.common.exception.ErrorCode;
import com.tomo.common.response.ErrorResponse;
import com.tomo.user.service.LoginSessionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** 요청의 Access Token과 로그인 세션을 검증해 인증 사용자를 등록한다. */
public class JwtAuthenticationFilter extends OncePerRequestFilter {
  private static final String BEARER_PREFIX = "Bearer ";

  private final AccessTokenProvider accessTokenProvider;
  private final LoginSessionService loginSessionService;
  private final ObjectWriter objectWriter;

  /**
   * Access 인증에 필요한 의존성을 주입한다.
   *
   * @param accessTokenProvider Access Token 검증기
   * @param loginSessionService 로그인 세션 조회 서비스
   * @param objectMapper 공통 오류 응답 직렬화 객체
   */
  public JwtAuthenticationFilter(
      AccessTokenProvider accessTokenProvider,
      LoginSessionService loginSessionService,
      ObjectMapper objectMapper) {
    this.accessTokenProvider = accessTokenProvider;
    this.loginSessionService = loginSessionService;
    this.objectWriter = objectMapper.writer();
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

    if (!authorization.startsWith(BEARER_PREFIX)) {
      reject(request, response);
      return;
    }

    AccessTokenClaims claims;

    try {
      String accessToken = authorization.substring(BEARER_PREFIX.length());
      claims = accessTokenProvider.verify(accessToken);
    } catch (BusinessException exception) {
      reject(request, response);
      return;
    }

    if (!loginSessionService.isValidAccessSession(claims.userId(), claims.loginSessionId())) {
      reject(request, response);
      return;
    }

    UsernamePasswordAuthenticationToken authentication =
        new UsernamePasswordAuthenticationToken(claims.userId(), null, List.of());

    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(authentication);
    SecurityContextHolder.setContext(context);

    filterChain.doFilter(request, response);
  }

  private void reject(HttpServletRequest request, HttpServletResponse response) throws IOException {
    SecurityContextHolder.clearContext();

    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());

    objectWriter.writeValue(
        response.getOutputStream(),
        ErrorResponse.from(ErrorCode.INVALID_ACCESS_TOKEN, request.getRequestId()));
  }
}
