package com.tomo.integration.redis;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** Redis 문자열 값의 저장, 조회, 삭제를 담당한다. */
@Component
public class RedisStringClient {
  private final StringRedisTemplate redisTemplate;

  /**
   * Redis 문자열 접근 객체를 주입한다.
   *
   * @param redisTemplate Redis 문자열 접근 객체
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Spring이 관리하는 StringRedisTemplate을 공유 의존성으로 사용하며 설정을 변경하지 않는다.")
  public RedisStringClient(StringRedisTemplate redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  /**
   * 키가 존재하지 않을 때만 만료 시간을 지정해 값을 저장한다.
   *
   * @param key Redis 키
   * @param value 저장할 값
   * @param ttl 유효 기간
   * @return 새로 저장했으면 true
   */
  public boolean setIfAbsent(String key, String value, Duration ttl) {
    return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, value, ttl));
  }

  /**
   * Redis에 저장된 값을 조회한다.
   *
   * @param key Redis 키
   * @return 저장된 값, 존재하지 않으면 null
   */
  public String get(String key) {
    return redisTemplate.opsForValue().get(key);
  }

  /**
   * 지정한 Redis 키를 삭제한다.
   *
   * @param key 삭제할 키
   */
  public void delete(String key) {
    redisTemplate.delete(key);
  }
}
