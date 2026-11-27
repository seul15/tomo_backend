package com.tomo.common.openapi;

import com.scalar.maven.core.ScalarProperties;
import com.scalar.maven.core.enums.ScalarTheme;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

/** Scalar API 문서 화면에 Catppuccin 테마와 파비콘을 적용한다. */
@Configuration
@ConditionalOnProperty(name = "scalar.enabled", havingValue = "true")
public class ScalarConfig {

  /**
   * Spring 설정 바인딩이 끝난 Scalar 속성에 테마와 파비콘을 적용한다.
   *
   * @param themeResource Catppuccin 테마 CSS 리소스
   * @param faviconResource Scalar 파비콘 PNG 리소스
   * @return Scalar 테마 후처리기
   * @throws IOException 테마 CSS나 파비콘 리소스를 읽지 못한 경우
   */
  @Bean
  public static BeanPostProcessor scalarThemePostProcessor(
      @Value("classpath:scalar/catppuccin.css") Resource themeResource,
      @Value("classpath:scalar/favicon.png") Resource faviconResource)
      throws IOException {
    String customCss = themeResource.getContentAsString(StandardCharsets.UTF_8);
    String faviconDataUri =
        "data:image/png;base64,"
            + Base64.getEncoder().encodeToString(faviconResource.getContentAsByteArray());
    return new BeanPostProcessor() {
      @Override
      public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof ScalarProperties properties) {
          properties.setTheme(ScalarTheme.NONE);
          properties.setCustomCss(customCss);
          properties.setFavicon(faviconDataUri);
        }
        return bean;
      }
    };
  }
}
