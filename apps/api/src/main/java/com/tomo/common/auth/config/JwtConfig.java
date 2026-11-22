package com.tomo.common.auth.config;

import com.tomo.common.auth.token.AccessTokenProvider;
import com.tomo.common.auth.token.SignupTokenProvider;
import io.jsonwebtoken.security.Keys;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 실행 환경에서 제공한 JWT 서명 키로 Access 및 Signup Token 제공자를 구성한다. */
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
   * Base64 인코딩된 서명 키를 해석하여 공통 JWT 서명 키를 구성한다.
   *
   * @param base64Key 외부에서 주입한 JWT 서명 키
   * @return Access/Signup Token 공통 서명 키
   * @throws IllegalArgumentException 서명 키 인코딩 또는 길이가 잘못된 경우
   */
  @Bean
  public SecretKey jwtSigningKey(@Value("${tomo.auth.jwt-secret-base64}") String base64Key) {
    return Keys.hmacShaKeyFor(Base64.getDecoder().decode(base64Key));
  }

  /**
   * Base64 서명 키에서 구성한 공통 키를 이용해 Access Token 검증기를 생성한다.
   *
   * @param signingKey 공통 JWT 서명 키
   * @param clock 공통 시계
   * @return 서명·검증을 담당하는 코덱
   */
  @Bean
  public AccessTokenProvider jwtAccessTokenCodec(SecretKey signingKey, Clock clock) {
    return new AccessTokenProvider(signingKey, clock);
  }

  /**
   * 동일한 서명 키와 가입 만료 정책으로 Signup Token 발급·검증기를 구성한다.
   *
   * @param signingKey 공통 JWT 서명 키
   * @param clock 공통 시계
   * @param expiration 가입 토큰 유효기간
   * @return 가입 전용 토큰 발급·검증기
   */
  @Bean
  public SignupTokenProvider signupTokenProvider(
      SecretKey signingKey,
      Clock clock,
      @Value("${tomo.auth.signup-token-expiration}") Duration expiration) {
    return new SignupTokenProvider(signingKey, clock, expiration);
  }
}
