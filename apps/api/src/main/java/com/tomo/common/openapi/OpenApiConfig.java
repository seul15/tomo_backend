package com.tomo.common.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** TOMO API 문서의 공통 정보를 설정한다. */
@Configuration
public class OpenApiConfig {
  /** Refresh Token 인증을 위한 Bearer Security Scheme 이름. */
  public static final String REFRESH_BEARER_AUTH = "refreshBearerAuth";

  /** Access JWT 인증을 위한 Bearer Security Scheme 이름. */
  public static final String BEARER_AUTH = "bearerAuth";

  /**
   * API 문서의 공통 정보와 Access JWT 및 Refresh Bearer 인증 방식을 구성한다.
   *
   * @return TOMO OpenAPI 문서 설정
   */
  @Bean
  public OpenAPI tomoOpenApi() {
    return new OpenAPI()
        .info(new Info().title("TOMO API").description("TOMO 백엔드 API 명세").version("v1"))
        .components(
            new Components()
                .addSecuritySchemes(
                    BEARER_AUTH,
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT"))
                .addSecuritySchemes(
                    REFRESH_BEARER_AUTH,
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("opaque")));
  }
}
