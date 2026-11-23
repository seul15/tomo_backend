package com.tomo.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tomo.common.auth.exception.AuthErrorCode;
import com.tomo.common.auth.token.AccessTokenProvider;
import com.tomo.common.exception.BusinessException;
import com.tomo.integration.redis.RedisStringClient;
import com.tomo.user.entity.LoginSession;
import com.tomo.user.entity.User;
import com.tomo.user.repository.LoginSessionRepository;
import com.tomo.user.repository.UserRepository;
import com.tomo.user.service.model.TokenRefreshResult;
import io.jsonwebtoken.Jwts;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
class LoginSessionConcurrencyIntegrationTest {
  @Container
  private static final GenericContainer<?> REDIS =
      new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379);

  private static LettuceConnectionFactory connectionFactory;
  private static StringRedisTemplate redisTemplate;
  private static RedissonClient lockClient;

  @Autowired private EntityManager entityManager;
  @Autowired private UserRepository userRepository;
  @Autowired private LoginSessionRepository loginSessionRepository;
  @Autowired private PlatformTransactionManager transactionManager;

  private TransactionTemplate transactionTemplate;
  private LoginSessionFacade facade;
  private LoginSessionService sessionService;
  private RefreshTokenService refreshTokenService;
  private Clock clock;
  private String userId;
  private long sessionId;
  private String initialRefreshToken;

  @BeforeAll
  static void initializeRedis() {
    RedisStandaloneConfiguration redisConfiguration =
        new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379));

    connectionFactory = new LettuceConnectionFactory(redisConfiguration);
    connectionFactory.afterPropertiesSet();

    redisTemplate = new StringRedisTemplate(connectionFactory);
    redisTemplate.afterPropertiesSet();

    Config config = new Config();
    config
        .useSingleServer()
        .setAddress("redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379));

    lockClient = Redisson.create(config);
  }

  @AfterAll
  static void closeRedis() {
    lockClient.shutdown();
    connectionFactory.destroy();
  }

  @BeforeEach
  void createActiveSession() {
    Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    clock = Clock.fixed(now, ZoneOffset.UTC);
    transactionTemplate = new TransactionTemplate(transactionManager);
    sessionService = new LoginSessionService(userRepository, loginSessionRepository, clock);
    refreshTokenService = new RefreshTokenService(new RedisStringClient(redisTemplate), clock);
    AccessTokenProvider accessTokenProvider =
        new AccessTokenProvider(Jwts.SIG.HS256.key().build(), clock);
    facade =
        new LoginSessionFacade(
            sessionService,
            refreshTokenService,
            accessTokenProvider,
            clock,
            transactionManager,
            lockClient,
            Duration.ofHours(1),
            Duration.ofDays(7));

    User user = userRepository.saveAndFlush(User.createPending());
    userId = user.getId();
    transactionTemplate.executeWithoutResult(
        status ->
            entityManager
                .createNativeQuery(
                    "update users "
                        + "set nickname = :nickname, account_status = 'ACTIVE' "
                        + "where users_id = :userId")
                .setParameter("nickname", "test-" + userId)
                .setParameter("userId", userId)
                .executeUpdate());

    LocalDateTime issuedAt = LocalDateTime.ofInstant(now, ZoneOffset.UTC);
    LocalDateTime expiresAt = issuedAt.plusDays(2);
    LoginSession session =
        loginSessionRepository.saveAndFlush(LoginSession.create(userId, issuedAt, expiresAt));
    sessionId = session.getId();
    initialRefreshToken = refreshTokenService.issue(sessionId, expiresAt);
  }

  @AfterEach
  void removeTestState() {
    if (sessionId > 0) {
      String key = "auth:refresh:session:" + sessionId;
      redisTemplate.delete(List.of(key, key + ":history"));
    }
    if (userId != null) {
      transactionTemplate.executeWithoutResult(
          status -> {
            loginSessionRepository.findById(sessionId).ifPresent(loginSessionRepository::delete);
            userRepository.findById(userId).ifPresent(userRepository::delete);
          });
    }
  }

  @Test
  @DisplayName("같은 Refresh Token의 동시 재발급은 한 번만 성공하고 DB 만료는 7일로 연장된다")
  void allowsOnlyOneConcurrentRefresh() throws Exception {
    // given
    ExecutorService executor = Executors.newFixedThreadPool(2);
    CountDownLatch start = new CountDownLatch(1);
    try {
      Future<Object> first =
          executor.submit(
              () -> {
                start.await();
                try {
                  return facade.refresh(initialRefreshToken);
                } catch (BusinessException exception) {
                  return exception.getErrorCode();
                }
              });
      Future<Object> second =
          executor.submit(
              () -> {
                start.await();
                try {
                  return facade.refresh(initialRefreshToken);
                } catch (BusinessException exception) {
                  return exception.getErrorCode();
                }
              });

      // when
      start.countDown();
      Object firstResult = first.get(20, TimeUnit.SECONDS);
      Object secondResult = second.get(20, TimeUnit.SECONDS);

      // then
      assertThat(List.of(firstResult, secondResult))
          .filteredOn(value -> value instanceof TokenRefreshResult)
          .hasSize(1);
      assertThat(List.of(firstResult, secondResult)).contains(AuthErrorCode.INVALID_REFRESH_TOKEN);
      LocalDateTime expectedExpiration =
          LocalDateTime.ofInstant(clock.instant().plus(Duration.ofDays(7)), ZoneOffset.UTC);
      assertThat(loginSessionRepository.findById(sessionId).orElseThrow().getExpiresAt())
          .isEqualTo(expectedExpiration);
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  @DisplayName("로그아웃과 재발급이 경합해도 폐기된 세션이 다시 활성화되지 않는다")
  void doesNotRecreateSessionAfterLogout() throws Exception {
    // given
    ExecutorService executor = Executors.newFixedThreadPool(2);
    CountDownLatch start = new CountDownLatch(1);
    try {
      Future<String> logout =
          executor.submit(
              () -> {
                start.await();
                facade.logout(userId, sessionId);
                return "LOGGED_OUT";
              });
      Future<String> refresh =
          executor.submit(
              () -> {
                start.await();
                try {
                  facade.refresh(initialRefreshToken);
                  return "REFRESHED";
                } catch (BusinessException exception) {
                  if (exception.getErrorCode() != AuthErrorCode.INVALID_REFRESH_TOKEN) {
                    throw exception;
                  }
                  return "REJECTED";
                }
              });

      // when
      start.countDown();
      String logoutResult = logout.get(20, TimeUnit.SECONDS);
      String refreshResult = refresh.get(20, TimeUnit.SECONDS);

      // then
      assertThat(logoutResult).isEqualTo("LOGGED_OUT");
      assertThat(refreshResult).isIn("REFRESHED", "REJECTED");
      assertThat(loginSessionRepository.findById(sessionId)).isEmpty();
      assertThat(redisTemplate.opsForValue().get("auth:refresh:session:" + sessionId))
          .isEqualTo("__REVOKED__");
      assertThat(sessionService.isValidAccessSession(userId, sessionId)).isFalse();
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  @DisplayName("10초 후 직전 Refresh Token 재사용은 해당 기기 세션만 폐기한다")
  void revokesAfterLatePreviousTokenReplay() {
    // given
    facade.refresh(initialRefreshToken);
    String key = "auth:refresh:session:" + sessionId;
    String historyKey = key + ":history";
    String stored = redisTemplate.opsForValue().get(historyKey);
    assertThat(stored).isNotNull();
    String[] segments = stored.split(":");
    assertThat(segments).hasSize(3);
    long oldRotationTime = System.currentTimeMillis() - Duration.ofMinutes(5).toMillis();
    redisTemplate
        .opsForValue()
        .set(
            historyKey,
            segments[0] + ":" + segments[1] + ":" + oldRotationTime,
            Duration.ofDays(7));

    // when
    // then
    assertThatThrownBy(() -> facade.refresh(initialRefreshToken))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN));
    assertThat(loginSessionRepository.findById(sessionId)).isEmpty();
    assertThat(redisTemplate.opsForValue().get(key)).isEqualTo("__REVOKED__");
  }

  @Test
  @DisplayName("10초 이내 직전 Refresh Token 재사용은 거부하되 로그인 세션을 유지한다")
  void rejectsPreviousWithinGraceWithoutRevokingSession() {
    // given
    TokenRefreshResult refreshed = facade.refresh(initialRefreshToken);

    assertThat(refreshed.refreshToken()).isNotEqualTo(initialRefreshToken);

    // when
    // then
    assertThatThrownBy(() -> facade.refresh(initialRefreshToken))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN));

    assertThat(loginSessionRepository.findById(sessionId)).isPresent();

    String key = "auth:refresh:session:" + sessionId;

    assertThat(redisTemplate.opsForValue().get(key)).isNotNull().isNotEqualTo("__REVOKED__");
  }
}
