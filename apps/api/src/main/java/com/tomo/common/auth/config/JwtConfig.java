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
   * 인증에 사용할 UTC 기준 시계를 생성한다.
   *
   * @return UTC 기준 시계
   */
  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }

  /**
   * Base64 인코딩된 서명 키를 이용해 Access Token 검증기를 생성한다.
   *
   * @param base64Key 외부에서 주입한 JWT 서명 키
   * @param clock 공통 시계
   * @return 서명·검증을 담당하는 코덱
   */
  @Bean
  @ConditionalOnProperty(name = "tomo.auth.jwt-secret-base64")
  public AccessTokenProvider jwtAccessTokenCodec(
      @Value("${tomo.auth.jwt-secret-base64}") String base64Key, Clock clock) {
    byte[] secret = Base64.getDecoder().decode(base64Key);
    return new AccessTokenProvider(Keys.hmacShaKeyFor(secret), clock);
  }
}
