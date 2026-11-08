package com.tomo.integration.redis;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Duration;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/** Redis 문자열 값의 저장, 조회, 삭제를 담당한다. */
@Component
public class RedisStringClient {
  private static final DefaultRedisScript<Long> REPLACE_IF_MATCHES_SCRIPT =
      new DefaultRedisScript<>(
          "if redis.call('GET', KEYS[1]) == ARGV[1] then "
              + "redis.call('PSETEX', KEYS[1], ARGV[3], ARGV[2]); "
              + "return 1 else return 0 end",
          Long.class);

  private static final DefaultRedisScript<Long> DELETE_IF_MATCHES_SCRIPT =
      new DefaultRedisScript<>(
          "if redis.call('GET', KEYS[1]) == ARGV[1] then "
              + "return redis.call('DEL', KEYS[1]) else return 0 end",
          Long.class);

  private static final DefaultRedisScript<Long> REISSUE_SCRIPT =
      new DefaultRedisScript<>(
          "if redis.call('GET', KEYS[1]) == ARGV[1] then "
              + "return 0 end "
              + "redis.call('PSETEX', KEYS[1], ARGV[3], ARGV[2]); "
              + "return 1",
          Long.class);

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
   * 기존 값이 일치할 때만 새 값과 TTL을 원자적으로 저장한다.
   *
   * @param key Redis 키
   * @param expectedValue 기존 값
   * @param newValue 새 값
   * @param ttl 새 TTL
   * @return 교체 성공 여부
   * @throws IllegalArgumentException TTL이 1밀리초 미만인 경우
   */
  public boolean compareAndSetWithTtl(
      String key, String expectedValue, String newValue, Duration ttl) {

    if (ttl.isZero() || ttl.isNegative() || ttl.toMillis() < 1) {
      throw new IllegalArgumentException("Redis TTL은 1밀리초 이상이어야 합니다.");
    }

    Long result =
        redisTemplate.execute(
            REPLACE_IF_MATCHES_SCRIPT,
            List.of(key),
            expectedValue,
            newValue,
            Long.toString(ttl.toMillis()));

    return Long.valueOf(1L).equals(result);
  }

  /**
   * 저장된 값이 일치하는 경우에만 Redis 키를 삭제한다.
   *
   * @param key Redis 키
   * @param expectedValue 삭제 대상 값
   * @return 삭제 성공 여부
   */
  public boolean deleteIfMatches(String key, String expectedValue) {
    Long result = redisTemplate.execute(DELETE_IF_MATCHES_SCRIPT, List.of(key), expectedValue);

    return Long.valueOf(1L).equals(result);
  }

  /**
   * Redis 키에 TTL과 함께 값을 저장한다.
   *
   * @param key Redis 키
   * @param value 저장할 값
   * @param ttl 유효 기간
   */
  public void set(String key, String value, Duration ttl) {
    redisTemplate.opsForValue().set(key, value, ttl);
  }

  /**
   * 세션 폐기 표시가 없을 때만 토큰 값을 교체한다.
   *
   * @param key Redis 키
   * @param revokedValue 세션 폐기 표시
   * @param newValue 새로운 값
   * @param ttl 유효 기간
   * @return 교체 성공 여부
   * @throws IllegalArgumentException TTL이 1밀리초 미만인 경우
   */
  public boolean replaceUnlessRevoked(
      String key, String revokedValue, String newValue, Duration ttl) {

    if (ttl.toMillis() <= 0) {
      throw new IllegalArgumentException("TTL은 1밀리초 이상이어야 합니다.");
    }

    Long result =
        redisTemplate.execute(
            REISSUE_SCRIPT, List.of(key), revokedValue, newValue, Long.toString(ttl.toMillis()));

    return Long.valueOf(1L).equals(result);
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
