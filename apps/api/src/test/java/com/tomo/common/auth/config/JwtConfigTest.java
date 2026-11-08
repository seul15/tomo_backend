package com.tomo.common.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tomo.common.auth.token.AccessTokenProvider;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class JwtConfigTest {
  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(JwtConfig.class);

  @Test
  @DisplayName("설정된 서명 키로 Access Token을 발급하고 검증한다")
  void createsCodecWithKey() {
    // given
    byte[] secret = new byte[32];
    secret[0] = 1;
    String encoded = Base64.getEncoder().encodeToString(secret);

    // when
    contextRunner
        .withPropertyValues("tomo.auth.jwt-secret-base64=" + encoded)
        .run(
            context -> {
              // then
              assertThat(context).hasSingleBean(AccessTokenProvider.class);

              AccessTokenProvider provider = context.getBean(AccessTokenProvider.class);
              String token = provider.issue("user-id", 10L, Instant.now().plusSeconds(600));

              String subject =
                  Jwts.parser()
                      .verifyWith(Keys.hmacShaKeyFor(secret))
                      .build()
                      .parseSignedClaims(token)
                      .getPayload()
                      .getSubject();

              assertThat(subject).isEqualTo("user-id");

              byte[] otherSecret = secret.clone();
              otherSecret[0] = 2;

              assertThatThrownBy(
                      () ->
                          Jwts.parser()
                              .verifyWith(Keys.hmacShaKeyFor(otherSecret))
                              .build()
                              .parseSignedClaims(token))
                  .isInstanceOf(JwtException.class);
            });
  }

  @Test
  @DisplayName("JWT 서명 키가 없으면 애플리케이션 설정에 실패한다")
  void rejectsMissingSigningKey() {
    // when
    contextRunner.run(
        context -> {
          // then
          assertThat(context).hasFailed();
        });
  }
}
