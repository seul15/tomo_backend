package com.tomo.common.logging;

import jakarta.servlet.DispatcherType;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 인증 필터를 포함한 HTTP 요청 처리에 요청 식별자 로깅을 적용한다. */
@Configuration
public class RequestLoggingConfig {

  /**
   * Security 필터 앞에서 요청 식별자를 등록하고 요청·비동기·오류 디스패치에 적용한다.
   *
   * @param securityProperties Security 필터 순서 설정
   * @return 요청 식별자 필터 등록 설정
   */
  @Bean
  public FilterRegistrationBean<RequestIdFilter> requestIdFilter(
      SecurityProperties securityProperties) {
    FilterRegistrationBean<RequestIdFilter> registration =
        new FilterRegistrationBean<>(new RequestIdFilter());
    registration.setOrder(securityProperties.getFilter().getOrder() - 1);
    registration.setDispatcherTypes(
        DispatcherType.REQUEST, DispatcherType.ASYNC, DispatcherType.ERROR);
    registration.setAsyncSupported(true);
    return registration;
  }
}
