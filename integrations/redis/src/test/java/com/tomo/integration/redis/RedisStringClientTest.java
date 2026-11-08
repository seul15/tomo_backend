package com.tomo.integration.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

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
}
