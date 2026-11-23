package com.tomo.common.auth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tomo.common.auth.exception.AuthErrorCode;
import com.tomo.common.auth.filter.JwtAuthenticationFilter;
import com.tomo.common.auth.session.AccessSessionValidator;
import com.tomo.common.auth.token.AccessTokenProvider;
import com.tomo.common.exception.CommonErrorCode;
import com.tomo.common.response.ErrorResponse;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/** 공개 진입점과 Access JWT 보호 API의 보안 정책을 정의한다. */
@Configuration
public class SecurityConfig {
  private static final Logger LOG = LoggerFactory.getLogger(SecurityConfig.class);

  /**
   * 인증되지 않은 요청에 공통 오류 응답을 반환한다.
   *
   * @param objectMapper 오류 응답 직렬화기
   * @return 인증 진입점
   */
  @Bean
  public AuthenticationEntryPoint authenticationEntryPoint(ObjectMapper objectMapper) {
    return (request, response, exception) -> {
      LOG.debug("인증되지 않은 요청 거부");

      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      response.setCharacterEncoding(StandardCharsets.UTF_8.name());

      objectMapper.writeValue(
          response.getOutputStream(),
          ErrorResponse.from(AuthErrorCode.INVALID_ACCESS_TOKEN, request.getRequestId()));
    };
  }

  /**
   * 접근 권한이 없는 요청에 공통 오류 응답을 반환한다.
   *
   * @param objectMapper 오류 응답 직렬화기
   * @return 접근 거부 처리기
   */
  @Bean
  public AccessDeniedHandler accessDeniedHandler(ObjectMapper objectMapper) {
    return (request, response, exception) -> {
      LOG.debug("접근 권한 없는 요청 거부");

      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      response.setCharacterEncoding(StandardCharsets.UTF_8.name());

      objectMapper.writeValue(
          response.getOutputStream(),
          ErrorResponse.from(CommonErrorCode.FORBIDDEN, request.getRequestId()));
    };
  }

  /**
   * JWT 필터 및 Method별 허용 경로를 구성한다. permitAll 경로의 실제 인증은 해당 API가 수행한다.
   *
   * @param http 보안 설정 대상
   * @param authenticationEntryPoint 인증 실패 처리기
   * @param accessDeniedHandler 권한 거부 처리기
   * @param accessTokenProvider Access JWT 검증기
   * @param accessSessionValidator DB 세션 검증기
   * @param objectMapper 오류 직렬화기
   * @return API 보안 필터 체인
   * @throws Exception 필터 체인 구성 실패 시
   */
  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      AuthenticationEntryPoint authenticationEntryPoint,
      AccessDeniedHandler accessDeniedHandler,
      AccessTokenProvider accessTokenProvider,
      AccessSessionValidator accessSessionValidator,
      ObjectMapper objectMapper)
      throws Exception {

    http.addFilterBefore(
        new JwtAuthenticationFilter(accessTokenProvider, accessSessionValidator, objectMapper),
        UsernamePasswordAuthenticationFilter.class);

    return http.csrf(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            authorize ->
                authorize
                    .dispatcherTypeMatchers(DispatcherType.ERROR)
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.GET,
                        "/scalar",
                        "/scalar/**",
                        "/v3/api-docs",
                        "/v3/api-docs/**",
                        "/v3/api-docs.yaml")
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.POST,
                        "/api/v1/auth/google",
                        "/api/v1/auth/kakao",
                        "/api/v1/auth/refresh",
                        "/api/v1/users/me/profile")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/auth/nickname")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            exception ->
                exception
                    .authenticationEntryPoint(authenticationEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler))
        .build();
  }
}
