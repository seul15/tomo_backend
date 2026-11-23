package com.tomo.integration.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

@ExtendWith(MockitoExtension.class)
class RedisStringClientTest {
  @Mock private StringRedisTemplate redisTemplate;
  @Mock private ValueOperations<String, String> valueOperations;

  @Test
  @DisplayName("Redis 키가 없으면 지정된 TTL로 값을 저장한다")
  void storesIfAbsent() {
    // given
    Duration ttl = Duration.ofMinutes(30);

    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.setIfAbsent("key", "hash", ttl)).thenReturn(true);

    RedisStringClient client = new RedisStringClient(redisTemplate);

    // when
    boolean stored = client.setIfAbsent("key", "hash", ttl);

    // then
    assertThat(stored).isTrue();
    verify(valueOperations).setIfAbsent("key", "hash", ttl);
  }

  @Test
  @DisplayName("Redis에 저장된 값을 조회한다")
  void readsValue() {
    // given
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.get("key")).thenReturn("hash");

    RedisStringClient client = new RedisStringClient(redisTemplate);

    // when
    String result = client.get("key");

    // then
    assertThat(result).isEqualTo("hash");
  }

  @Test
  @DisplayName("지정한 Redis 키를 삭제한다")
  void deletesKey() {
    // given
    RedisStringClient client = new RedisStringClient(redisTemplate);

    // when
    client.delete("key");

    // then
    verify(redisTemplate).delete("key");
  }

  @Test
  @DisplayName("세션 폐기 표시를 TTL과 함께 저장한다")
  void storesRevocationMarker() {
    // given
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    RedisStringClient client = new RedisStringClient(redisTemplate);

    // when
    client.set("key", "__REVOKED__", Duration.ofMinutes(30));

    // then
    verify(valueOperations).set("key", "__REVOKED__", Duration.ofMinutes(30));
  }

  @Test
  @DisplayName("Rotation Lua 결과 코드를 Redis 클라이언트가 그대로 전달한다")
  void returnsRotationResult() {
    // given
    when(redisTemplate.execute(
            org.mockito.ArgumentMatchers.<RedisScript<Long>>any(),
            eq(List.of("auth:refresh:session:10", "auth:refresh:session:10:history")),
            eq("a".repeat(64)),
            eq("b".repeat(64)),
            eq("604800000"),
            eq("10000"),
            eq("1")))
        .thenReturn(1L);
    RedisStringClient client = new RedisStringClient(redisTemplate);

    // when
    long status =
        client.rotateWithHistory(
            "auth:refresh:session:10",
            "a".repeat(64),
            "b".repeat(64),
            Duration.ofDays(7),
            Duration.ofSeconds(10),
            true);

    // then
    assertThat(status).isEqualTo(1L);
  }

  @Test
  @DisplayName("조건부 Redis 토큰 폐기 성공 결과를 반환한다")
  void revokesMatchingHash() {
    // given
    when(redisTemplate.execute(
            org.mockito.ArgumentMatchers.<RedisScript<Long>>any(),
            eq(List.of("auth:refresh:session:10", "auth:refresh:session:10:history")),
            eq("a".repeat(64)),
            eq("__REVOKED__"),
            eq("604800000")))
        .thenReturn(1L);
    RedisStringClient client = new RedisStringClient(redisTemplate);

    // when
    boolean revoked =
        client.revokeIfCurrentMatches(
            "auth:refresh:session:10", "a".repeat(64),
            "__REVOKED__", Duration.ofDays(7));

    // then
    assertThat(revoked).isTrue();
  }
}
