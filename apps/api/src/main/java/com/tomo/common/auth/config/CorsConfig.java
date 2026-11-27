package com.tomo.common.auth.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/** 실행 환경별 허용 출처와 브라우저 교차 출처 요청 정책을 구성한다. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties
public class CorsConfig {

  /**
   * YAML의 tomo.cors 설정을 바인딩한다. 허용 출처가 없으면 교차 출처 요청을 허용하지 않는다.
   *
   * @return 브라우저 요청에 적용할 CORS 정책
   */
  @Bean
  @ConfigurationProperties("tomo.cors")
  public CorsConfiguration corsConfiguration() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedMethods(
        List.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("*"));
    configuration.setAllowCredentials(false);
    configuration.setMaxAge(3600L);
    return configuration;
  }

  /**
   * API 경로에 CORS 정책을 연결한다.
   *
   * @param corsConfiguration 실행 환경의 CORS 정책
   * @return Security 필터에서 사용할 경로별 CORS 정책
   */
  @Bean
  public UrlBasedCorsConfigurationSource corsConfigurationSource(
      CorsConfiguration corsConfiguration) {
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", corsConfiguration);
    return source;
  }
}
