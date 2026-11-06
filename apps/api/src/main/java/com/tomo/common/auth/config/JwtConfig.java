package com.tomo.common.auth.config;

import com.tomo.common.auth.token.AccessTokenProvider;
import io.jsonwebtoken.security.Keys;
import java.time.Clock;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 실행 환경에서 제공한 JWT 서명 키로 Access Token 코덱을 구성한다. */
@Configuration(proxyBeanMethods = false)
public class JwtConfig {
  /**
   * Base64 인코딩된 256비트 이상의 서명 키를 이용해 코덱을 생성한다.
   *
   * @param base64Key 외부에서 주입한 Base64 JWT 서명 키
   * @return 서명·검증을 담당하는 코덱
   */
  @Bean
  @ConditionalOnProperty(name = "tomo.auth.jwt-secret-base64")
  public AccessTokenProvider jwtAccessTokenCodec(
      @Value("${tomo.auth.jwt-secret-base64}") String base64Key) {
    byte[] secret = Base64.getDecoder().decode(base64Key);
    return new AccessTokenProvider(Keys.hmacShaKeyFor(secret), Clock.systemUTC());
  }
}
