package com.tomo.common.auth.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.tomo.common.auth.token.AccessTokenProvider;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class JwtConfigTest {
  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(JwtConfig.class);

  @Test
  @DisplayName("서명 키가 설정되면 JWT 코덱을 생성한다")
  void createsCodecWithKey() {
    // given
    byte[] secret = new byte[32];
    String encoded = Base64.getEncoder().encodeToString(secret);

    // when
    contextRunner
        .withPropertyValues("tomo.auth.jwt-secret-base64=" + encoded)
        .run(
            context -> {
              // then
              assertThat(context).hasSingleBean(AccessTokenProvider.class);
            });
  }
}
