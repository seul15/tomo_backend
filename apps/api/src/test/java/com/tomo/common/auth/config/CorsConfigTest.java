package com.tomo.common.auth.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.cors.DefaultCorsProcessor;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

class CorsConfigTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner().withUserConfiguration(CorsConfig.class);

  @Test
  @DisplayName("YAML 목록 형태의 복수 출처와 허용 메서드 설정을 요청에 적용한다")
  void bindsOriginListAndMethods() {
    // given
    ApplicationContextRunner configuredRunner =
        runner.withPropertyValues(
            "tomo.cors.allowed-origins[0]=https://first.example.com",
            "tomo.cors.allowed-origins[1]=https://second.example.com",
            "tomo.cors.allowed-methods[0]=GET");

    // when
    configuredRunner.run(
        context -> {
          MockHttpServletRequest request = preflight("https://second.example.com", "POST");
          UrlBasedCorsConfigurationSource source =
              context.getBean(UrlBasedCorsConfigurationSource.class);
          MockHttpServletResponse response = new MockHttpServletResponse();

          // then
          assertThat(source.getCorsConfiguration(request).getAllowedOrigins())
              .containsExactly("https://first.example.com", "https://second.example.com");
          assertThat(
                  new DefaultCorsProcessor()
                      .processRequest(source.getCorsConfiguration(request), request, response))
              .isFalse();
          assertThat(response.getStatus()).isEqualTo(403);
        });
  }

  @Test
  @DisplayName("허용 출처가 설정되지 않으면 교차 출처 요청을 거부한다")
  void rejectsOriginsWhenUnconfigured() {
    // when
    runner.run(
        context -> {
          MockHttpServletRequest request = preflight("https://unknown.example.com", "GET");
          UrlBasedCorsConfigurationSource source =
              context.getBean(UrlBasedCorsConfigurationSource.class);
          MockHttpServletResponse response = new MockHttpServletResponse();

          // then
          assertThat(
                  new DefaultCorsProcessor()
                      .processRequest(source.getCorsConfiguration(request), request, response))
              .isFalse();
          assertThat(response.getStatus()).isEqualTo(403);
        });
  }

  private MockHttpServletRequest preflight(String origin, String method) {
    MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/v1/todos");
    request.setServletPath("/api/v1/todos");
    request.addHeader(HttpHeaders.ORIGIN, origin);
    request.addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, method);
    return request;
  }
}
