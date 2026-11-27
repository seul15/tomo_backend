package com.tomo.common.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import com.scalar.maven.core.enums.ScalarTheme;
import com.scalar.maven.webmvc.SpringBootScalarProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.webmvc.scalar.ScalarConfiguration;
import org.springdoc.webmvc.scalar.ScalarWebMvcController;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

class ScalarConfigTest {

  @Test
  @DisplayName("springdoc 컨트롤러와 충돌 없이 Scalar 화면에 Catppuccin 테마와 파비콘을 적용한다")
  void appliesThemeToSpringdocController() {
    // given
    WebApplicationContextRunner runner =
        new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ScalarConfiguration.class))
            .withUserConfiguration(ScalarConfig.class, TestConfig.class)
            .withPropertyValues("scalar.enabled=true", "scalar.url=/v3/api-docs");

    // when
    runner.run(
        context -> {
          // then
          assertThat(context).hasNotFailed().hasSingleBean(ScalarWebMvcController.class);
          SpringBootScalarProperties properties = context.getBean(SpringBootScalarProperties.class);
          assertThat(properties.getTheme()).isEqualTo(ScalarTheme.NONE);
          assertThat(properties.getUrl()).isEqualTo("/v3/api-docs");
          assertThat(properties.getFavicon()).startsWith("data:image/png;base64,");
          ResponseEntity<String> response =
              context.getBean(ScalarWebMvcController.class).getDocs(new MockHttpServletRequest());
          assertThat(response.getBody())
              .contains(
                  "#eff1f5", "#1e1e2e", ".light-mode", ".dark-mode", "data:image/png;base64,");
        });
  }

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(SpringDocConfigProperties.class)
  static class TestConfig {

    @Bean
    SpringDocConfiguration springDocConfiguration() {
      return new SpringDocConfiguration();
    }
  }
}
