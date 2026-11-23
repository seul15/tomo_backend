package com.tomo.integration.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class RedisStringClientIntegrationTest {
  private static final String KEY = "tomo:test:refresh:integration";
  private static final String OLD = "a".repeat(64);
  private static final String NEXT = "b".repeat(64);
  private static final String AFTER = "c".repeat(64);
  private static final Duration TTL = Duration.ofMinutes(2);

  @Container
  private static final GenericContainer<?> REDIS =
      new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379);

  private static LettuceConnectionFactory connectionFactory;
  private static StringRedisTemplate redisTemplate;
  private static RedisStringClient client;

  @BeforeAll
  static void setUpRedis() {
    RedisStandaloneConfiguration configuration =
        new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379));
    connectionFactory = new LettuceConnectionFactory(configuration);
    connectionFactory.afterPropertiesSet();
    redisTemplate = new StringRedisTemplate(connectionFactory);
    redisTemplate.afterPropertiesSet();
    client = new RedisStringClient(redisTemplate);
  }

  @AfterEach
  void clearKey() {
    redisTemplate.delete(List.of(KEY, KEY + ":history"));
  }

  @AfterAll
  static void closeRedis() {
    connectionFactory.destroy();
  }

  @Test
  @DisplayName("Lua는 토큰 교체와 직전 해시·시각 기록을 원자적으로 처리한다")
  void rotatesAndTracksPrevious() {
    // given
    assertThat(client.setIfAbsent(KEY, OLD, TTL)).isTrue();

    // when
    long first = client.rotateWithHistory(KEY, OLD, NEXT, TTL, Duration.ofSeconds(10), true);
    long retry = client.rotateWithHistory(KEY, OLD, AFTER, TTL, Duration.ofSeconds(10), true);

    // then
    assertThat(first).isEqualTo(1L);
    assertThat(retry).isEqualTo(2L);
    assertThat(redisTemplate.opsForValue().get(KEY)).isEqualTo(NEXT);
    assertThat(redisTemplate.opsForValue().get(KEY + ":history"))
        .startsWith(NEXT + ":" + OLD + ":");
    Long remainingSeconds = redisTemplate.getExpire(KEY, TimeUnit.SECONDS);
    assertThat(remainingSeconds).isNotNull().isPositive().isLessThanOrEqualTo(120L);
  }

  @Test
  @DisplayName("유예시간이 지난 직전 토큰은 재사용 상태 3을 반환한다")
  void identifiesLateReplay() throws InterruptedException {
    // given
    client.setIfAbsent(KEY, OLD, TTL);
    assertThat(client.rotateWithHistory(KEY, OLD, NEXT, TTL, Duration.ofMillis(10), true))
        .isEqualTo(1L);
    Thread.sleep(80);

    // when
    long result = client.rotateWithHistory(KEY, OLD, AFTER, TTL, Duration.ofMillis(10), true);

    // then
    assertThat(result).isEqualTo(3L);
  }

  @Test
  @DisplayName("직전보다 오래된 토큰은 세션 폐기 대상이 아니라 불일치 상태 0이다")
  void ignoresOlderHistory() {
    // given
    assertThat(client.setIfAbsent(KEY, OLD, TTL)).isTrue();

    assertThat(client.rotateWithHistory(KEY, OLD, NEXT, TTL, Duration.ofSeconds(10), true))
        .isEqualTo(1L);

    assertThat(client.rotateWithHistory(KEY, NEXT, AFTER, TTL, Duration.ofSeconds(10), true))
        .isEqualTo(1L);

    // when
    long result = client.rotateWithHistory(KEY, OLD, NEXT, TTL, Duration.ofSeconds(10), true);

    // then
    assertThat(result).isZero();
  }

  @Test
  @DisplayName("비활성 계정의 현재 토큰은 변경 없이 상태 4로 거부한다")
  void rejectsInactiveAccount() {
    // given
    client.setIfAbsent(KEY, OLD, TTL);

    // when
    long result = client.rotateWithHistory(KEY, OLD, NEXT, TTL, Duration.ofSeconds(10), false);

    // then
    assertThat(result).isEqualTo(4L);
    assertThat(redisTemplate.opsForValue().get(KEY)).isEqualTo(OLD);
  }

  @Test
  @DisplayName("조건부 폐기는 예상한 현재 해시에만 성공하며 폐기된 키는 재발급할 수 없다")
  void revokesOnlyCurrentHash() {
    // given
    client.setIfAbsent(KEY, OLD, TTL);

    // when
    boolean wrong = client.revokeIfCurrentMatches(KEY, NEXT, "__REVOKED__", TTL);
    boolean correct = client.revokeIfCurrentMatches(KEY, OLD, "__REVOKED__", TTL);
    long afterRevoke = client.rotateWithHistory(KEY, OLD, NEXT, TTL, Duration.ofSeconds(10), true);

    // then
    assertThat(wrong).isFalse();
    assertThat(correct).isTrue();
    assertThat(afterRevoke).isZero();
    assertThat(redisTemplate.opsForValue().get(KEY)).isEqualTo("__REVOKED__");
  }

  @Test
  @DisplayName("동일 토큰 두 요청 중 하나만 성공하고 다른 요청은 유예 내 재사용으로 거부된다")
  void serializesConcurrentRotation() throws Exception {
    // given
    client.setIfAbsent(KEY, OLD, TTL);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    CountDownLatch start = new CountDownLatch(1);
    try {
      Future<Long> first =
          executor.submit(
              () -> {
                start.await();
                return client.rotateWithHistory(KEY, OLD, NEXT, TTL, Duration.ofMinutes(1), true);
              });
      Future<Long> second =
          executor.submit(
              () -> {
                start.await();
                return client.rotateWithHistory(KEY, OLD, AFTER, TTL, Duration.ofMinutes(1), true);
              });

      // when
      start.countDown();
      long firstResult = first.get(10, TimeUnit.SECONDS);
      long secondResult = second.get(10, TimeUnit.SECONDS);

      // then
      assertThat(List.of(firstResult, secondResult)).containsExactlyInAnyOrder(1L, 2L);
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  @DisplayName("DB 갱신 실패 시 신규 토큰 해시를 직전 토큰 해시로 복원한다")
  void restoresPreviousTokenAfterFailure() {
    // given
    assertThat(client.setIfAbsent(KEY, OLD, TTL)).isTrue();

    assertThat(client.rotateWithHistory(KEY, OLD, NEXT, TTL, Duration.ofSeconds(10), true))
        .isEqualTo(1L);

    // when
    boolean restored = client.restorePreviousIfCurrentMatches(KEY, NEXT, OLD, TTL);

    // then
    assertThat(restored).isTrue();
    assertThat(redisTemplate.opsForValue().get(KEY)).isEqualTo(OLD);

    assertThat(client.rotateWithHistory(KEY, OLD, AFTER, TTL, Duration.ofSeconds(10), true))
        .isEqualTo(1L);
  }

  @Test
  @DisplayName("구버전 복합 해시를 기존 TTL로 변환하고 새 버전에서 교체한다")
  void migratesLegacyCompositeValue() {
    assertThat(client.setIfAbsent(KEY, NEXT + ":" + OLD + ":1", TTL)).isTrue();

    long result = client.rotateWithHistory(KEY, NEXT, AFTER, TTL, Duration.ofSeconds(10), true);

    assertThat(result).isEqualTo(1L);
    assertThat(redisTemplate.opsForValue().get(KEY)).isEqualTo(AFTER);
    assertThat(redisTemplate.opsForValue().get(KEY + ":history"))
        .startsWith(AFTER + ":" + NEXT + ":");
  }

  @Test
  @DisplayName("기본 키가 이전 인스턴스에 의해 변경되면 오래된 이력으로 폐기하지 않는다")
  void ignoresStaleHistoryFromOldInstance() {
    assertThat(client.setIfAbsent(KEY, OLD, TTL)).isTrue();
    assertThat(client.rotateWithHistory(KEY, OLD, NEXT, TTL, Duration.ofSeconds(10), true))
        .isEqualTo(1L);

    // 이전 인스턴스가 기본 키만 교체한 상황
    redisTemplate.opsForValue().set(KEY, AFTER, TTL);

    long result = client.rotateWithHistory(KEY, OLD, NEXT, TTL, Duration.ofSeconds(10), true);

    assertThat(result).isZero();
    assertThat(redisTemplate.opsForValue().get(KEY)).isEqualTo(AFTER);
  }
}
