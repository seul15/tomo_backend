package com.tomo.common.auth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tomo.common.exception.ErrorCode;
import com.tomo.common.response.ErrorResponse;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;

/** API 문서 공개 경로와 기본 인증 정책을 설정한다. */
@Configuration
public class SecurityConfig {
  /**
   * 인증 정보가 없는 요청에 공통 오류 응답을 반환한다.
   *
   * @param objectMapper JSON 직렬화 객체
   * @return 인증 실패 처리기
   */
  @Bean
  public AuthenticationEntryPoint authenticationEntryPoint(ObjectMapper objectMapper) {
    return (request, response, exception) -> {
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      response.setCharacterEncoding(StandardCharsets.UTF_8.name());

      objectMapper.writeValue(
          response.getOutputStream(),
          ErrorResponse.from(ErrorCode.UNAUTHORIZED, request.getRequestId()));
    };
  }

  /**
   * API 문서 조회를 허용하고 그 외 요청에는 인증을 요구한다.
   *
   * @param http HTTP 보안 설정
   * @param authenticationEntryPoint 인증 실패 처리기
   * @return 애플리케이션 보안 필터 체인
   * @throws Exception 필터 체인 구성에 실패한 경우
   */
  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http, AuthenticationEntryPoint authenticationEntryPoint) throws Exception {
    return http.formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
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
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            exception -> exception.authenticationEntryPoint(authenticationEntryPoint))
        .build();
  }
}
