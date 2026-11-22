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
  private static final DefaultRedisScript<Long> ROTATE_WITH_HISTORY_SCRIPT =
      new DefaultRedisScript<>(
          "local value = redis.call('GET', KEYS[1]); "
              + "if not value then return 0 end; "
              + "local current, previous, changedAt = string.match(value, "
              + "'^([0-9a-f]+):([0-9a-f]+):(%d+)$'); "
              + "if not current then current = value end; "
              + "local currentTime = redis.call('TIME'); "
              + "local now = tonumber(currentTime[1])*1000 + math.floor(tonumber(currentTime[2])/1000); "
              + "if current == ARGV[1] then "
              + "if ARGV[5] == '0' then return 4 end; "
              + "redis.call('PSETEX', KEYS[1], ARGV[3], ARGV[2] .. ':' .. current .. ':' .. now); "
              + "return 1 end; "
              + "if previous and previous == ARGV[1] then "
              + "if now - tonumber(changedAt) <= tonumber(ARGV[4]) then return 2 end; "
              + "return 3 end; "
              + "return 0",
          Long.class);

  private static final DefaultRedisScript<Long> REVOKE_IF_CURRENT_SCRIPT =
      new DefaultRedisScript<>(
          "local value = redis.call('GET', KEYS[1]); "
              + "if not value then return 0 end; "
              + "local current = string.match(value, '^([0-9a-f]+):') or value; "
              + "if current == ARGV[1] then "
              + "redis.call('PSETEX', KEYS[1], ARGV[3], ARGV[2]); return 1 end; "
              + "return 0",
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
   * 지정한 Redis 키를 삭제한다.
   *
   * @param key 삭제할 키
   */
  public void delete(String key) {
    redisTemplate.delete(key);
  }

  /**
   * 한 Redis 키에서 현재 토큰 해시를 교체하고 직전 해시와 교체 시각을 원자적으로 기록한다.
   *
   * @param key 로그인 세션별 Redis 키
   * @param expectedHash 요청 Refresh Token 해시
   * @param nextHash 신규 Refresh Token 해시
   * @param ttl 교체 후 토큰 수명
   * @param grace 직전 토큰 재사용 유예시간
   * @param activeUser DB에서 확인한 ACTIVE 계정 여부
   * @return 1=교체, 2=유예시간 내 직전 토큰, 3=유예 초과 직전 토큰, 4=비활성 회원, 0=불일치
   * @throws IllegalArgumentException TTL 또는 유예시간이 올바르지 않은 경우
   * @throws IllegalStateException Redis Lua 스크립트 실행 결과가 null인 경우
   */
  public long rotateWithHistory(
      String key,
      String expectedHash,
      String nextHash,
      Duration ttl,
      Duration grace,
      boolean activeUser) {
    long ttlMillis = requirePositiveTtlMillis(ttl);
    if (grace == null || grace.isNegative()) {
      throw new IllegalArgumentException("Redis 토큰 재사용 유예시간이 올바르지 않습니다.");
    }
    Long result =
        redisTemplate.execute(
            ROTATE_WITH_HISTORY_SCRIPT,
            List.of(key),
            expectedHash,
            nextHash,
            Long.toString(ttlMillis),
            Long.toString(grace.toMillis()),
            activeUser ? "1" : "0");
    if (result == null) {
      throw new IllegalStateException("Redis Refresh Token 교체 결과가 없습니다.");
    }
    return result;
  }

  /**
   * Redis 현재 토큰 해시가 예상값과 같을 때만 폐기 표시로 교체한다.
   *
   * @param key 로그인 세션별 Redis 키
   * @param expectedHash 폐기 대상 현재 토큰 해시
   * @param revokedValue 폐기 표시 값
   * @param ttl 폐기 표시 유지기간
   * @return 예상 해시가 현재 값과 일치하여 폐기했으면 true
   * @throws IllegalArgumentException TTL이 유효하지 않은 경우
   */
  public boolean revokeIfCurrentMatches(
      String key, String expectedHash, String revokedValue, Duration ttl) {
    long ttlMillis = requirePositiveTtlMillis(ttl);
    Long result =
        redisTemplate.execute(
            REVOKE_IF_CURRENT_SCRIPT,
            List.of(key),
            expectedHash,
            revokedValue,
            Long.toString(ttlMillis));
    return Long.valueOf(1L).equals(result);
  }

  /**
   * Redis에 전달할 TTL이 1밀리초 이상인지 검증하고 밀리초 단위로 변환한다.
   *
   * @param ttl 검증할 만료기간
   * @return 밀리초 단위 TTL
   * @throws IllegalArgumentException TTL이 null이거나 1밀리초 미만인 경우
   */
  private static long requirePositiveTtlMillis(Duration ttl) {
    if (ttl == null || ttl.isZero() || ttl.isNegative() || ttl.toMillis() < 1) {
      throw new IllegalArgumentException("Redis TTL은 1밀리초 이상이어야 합니다.");
    }
    return ttl.toMillis();
  }
}
