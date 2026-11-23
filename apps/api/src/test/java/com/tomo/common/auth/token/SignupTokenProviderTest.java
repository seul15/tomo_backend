package com.tomo.common.auth.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tomo.common.auth.exception.AuthErrorCode;
import com.tomo.common.exception.BusinessException;
import io.jsonwebtoken.Jwts;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SignupTokenProviderTest {
  private static final Instant NOW = Instant.parse("2026-11-07T00:00:00Z");
  private static final SecretKey KEY = Jwts.SIG.HS256.key().build();

  @Test
  @DisplayName("Signup Token은 10분 이내에 서명과 타입을 검증하여 사용자 ID를 반환한다")
  void verifiesSignupToken() {
    // given
    SignupTokenProvider provider =
        new SignupTokenProvider(KEY, Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofMinutes(10));
    String token = provider.issue("pending-user");

    // when
    String userId = provider.verify(token);

    // then
    assertThat(userId).isEqualTo("pending-user");
  }

  @Test
  @DisplayName("10분이 지난 Signup Token은 거부한다")
  void rejectsExpiredSignupToken() {
    // given
    SignupTokenProvider issuer =
        new SignupTokenProvider(KEY, Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofMinutes(10));
    SignupTokenProvider verifier =
        new SignupTokenProvider(
            KEY, Clock.fixed(NOW.plusSeconds(601), ZoneOffset.UTC), Duration.ofMinutes(10));
    String token = issuer.issue("pending-user");

    // when
    // then
    assertThatThrownBy(() -> verifier.verify(token))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_SIGNUP_TOKEN));
  }

  @Test
  @DisplayName("다른 비밀키로 서명된 Signup Token은 거부한다")
  void rejectsForgedSignupToken() {
    // given
    SecretKey otherKey = Jwts.SIG.HS256.key().build();
    SignupTokenProvider issuer =
        new SignupTokenProvider(KEY, Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofMinutes(10));
    SignupTokenProvider verifier =
        new SignupTokenProvider(otherKey, Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofMinutes(10));
    String token = issuer.issue("pending-user");

    // when
    // then
    assertThatThrownBy(() -> verifier.verify(token))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_SIGNUP_TOKEN));
  }

  @Test
  @DisplayName("Access Token과 Signup Token은 서로의 검증기를 통과하지 못한다")
  void separatesTokenTypes() {
    // given
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    SignupTokenProvider signup = new SignupTokenProvider(KEY, clock, Duration.ofMinutes(10));
    AccessTokenProvider access = new AccessTokenProvider(KEY, clock);
    String signupJwt = signup.issue("pending-user");
    String accessJwt = access.issue("active-user", 10L, NOW.plusSeconds(3600));

    // when
    // then
    assertThatThrownBy(() -> access.verify(signupJwt))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_ACCESS_TOKEN));
    assertThatThrownBy(() -> signup.verify(accessJwt))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_SIGNUP_TOKEN));
  }
}
