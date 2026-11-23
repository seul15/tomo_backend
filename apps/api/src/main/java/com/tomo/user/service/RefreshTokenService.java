package com.tomo.user.service;

import com.tomo.common.auth.exception.AuthErrorCode;
import com.tomo.common.exception.BusinessException;
import com.tomo.integration.redis.RedisStringClient;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Redis Refresh Token의 발급·교체·폐기를 담당하며 DB 세션 변경은 수행하지 않는다. */
@Service
public class RefreshTokenService {
  private static final Logger LOG = LoggerFactory.getLogger(RefreshTokenService.class);
  private static final String KEY_PREFIX = "auth:refresh:session:";
  private static final String REVOKED_VALUE = "__REVOKED__";
  private static final int RANDOM_BYTES = 32;
  private static final int MAX_TOKEN_LENGTH = 64;
  static final Duration REUSE_GRACE = Duration.ofSeconds(10);
  private static final Pattern TOKEN_PATTERN =
      Pattern.compile("([1-9][0-9]*)\\.([A-Za-z0-9_-]{43})");

  private final RedisStringClient redisStringClient;
  private final Clock clock;
  private final SecureRandom secureRandom = new SecureRandom();

  /**
   * Refresh Token 연동 기능을 초기화한다.
   *
   * @param redisStringClient Redis 문자열 연동
   * @param clock 현재 시각 기준
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Spring이 관리하는 RedisStringClient를 공유 의존성으로 주입받으며 외부에 노출하지 않는다.")
  public RefreshTokenService(RedisStringClient redisStringClient, Clock clock) {
    this.redisStringClient = redisStringClient;
    this.clock = clock;
  }

  /**
   * Refresh Token에서 로그인 세션 ID를 추출한다. ID의 존재는 DB에서 별도로 검증한다.
   *
   * @param token Refresh Token 원문
   * @return 형식 검증을 통과한 세션 ID
   * @throws BusinessException Refresh Token 형식이 잘못된 경우
   */
  public long extractSessionId(String token) {
    if (token == null || token.length() > MAX_TOKEN_LENGTH) {
      throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
    }
    Matcher matcher = TOKEN_PATTERN.matcher(token);
    if (!matcher.matches()) {
      throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
    }
    try {
      return Long.parseLong(matcher.group(1));
    } catch (NumberFormatException exception) {
      throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
    }
  }

  /**
   * DB에 이미 생성된 로그인 세션에 최초 Refresh Token을 발급한다.
   *
   * <p>동일 세션에 토큰이 있거나 폐기 표시가 남아 있으면 덮어쓰지 않는다.
   *
   * @param sessionId 세션 ID
   * @param expiresAt DB 세션 만료 시각(UTC)
   * @return 신규 Refresh Token 원문
   * @throws BusinessException 세션이 만료되었거나 기존 토큰 키가 존재하는 경우
   */
  public String issue(long sessionId, LocalDateTime expiresAt) {
    if (sessionId <= 0 || expiresAt == null) {
      throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
    }
    Duration ttl = Duration.between(clock.instant(), expiresAt.toInstant(ZoneOffset.UTC));
    if (ttl.toMillis() < 1) {
      throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
    }

    String token = newToken(sessionId);
    if (!redisStringClient.setIfAbsent(key(sessionId), hash(token), ttl)) {
      throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
    }
    LOG.info("Refresh Token 최초 발급. sessionId={}", sessionId);
    return token;
  }

  /**
   * 현재 Refresh Token을 새로운 토큰으로 교체하거나 직전 토큰 재사용 여부를 판정한다.
   *
   * @param token 기존 Refresh Token 원문
   * @param sessionId 잠금 조회로 검증한 로그인 세션 ID
   * @param ttl 정상 교체 후 Redis 유지기간
   * @param activeUser DB에서 확인한 ACTIVE 회원 여부
   * @return 교체 결과 및 교체에 성공한 경우 새 토큰 원문
   * @throws BusinessException 잘못된 세션 ID가 전달된 경우
   */
  public Rotation rotate(String token, long sessionId, Duration ttl, boolean activeUser) {
    if (extractSessionId(token) != sessionId || sessionId <= 0) {
      throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
    }
    String next = newToken(sessionId);
    long status =
        redisStringClient.rotateWithHistory(
            key(sessionId), hash(token), hash(next), ttl, REUSE_GRACE, activeUser);
    if (status == 1) {
      LOG.info("Refresh Token 교체 완료. sessionId={}", sessionId);
      return new Rotation(RotationStatus.ROTATED, next);
    }
    if (status == 2) {
      return new Rotation(RotationStatus.PREVIOUS_WITHIN_GRACE, null);
    }
    if (status == 3) {
      return new Rotation(RotationStatus.PREVIOUS_AFTER_GRACE, null);
    }
    if (status == 4) {
      return new Rotation(RotationStatus.FORBIDDEN, null);
    }
    return new Rotation(RotationStatus.INVALID, null);
  }

  /**
   * DB 세션을 삭제하기 전에 해당 세션의 Refresh Token과 재사용 이력을 폐기한다.
   *
   * @param sessionId 세션 ID
   * @param expiresAt 현재 DB 세션 만료 시각(UTC)
   * @throws IllegalArgumentException 세션 ID가 유효하지 않거나 만료 시각이 null인 경우
   */
  public void revokeBeforeSessionDeletion(long sessionId, LocalDateTime expiresAt) {
    if (sessionId <= 0 || expiresAt == null) {
      throw new IllegalArgumentException("로그인 세션 정보가 필요합니다.");
    }

    Duration ttl = Duration.between(clock.instant(), expiresAt.toInstant(ZoneOffset.UTC));
    if (ttl.toMillis() < 1) {
      ttl = Duration.ofMillis(1);
    }

    redisStringClient.set(key(sessionId), REVOKED_VALUE, ttl);
    LOG.info("Refresh Token 세션 폐기. sessionId={}", sessionId);
  }

  /**
   * DB 커밋 실패 후 새로 저장한 토큰이 아직 현재 토큰일 때만 폐기한다.
   *
   * @param sessionId 세션 ID
   * @param newlyIssuedToken DB 커밋 전에 생성한 새 Refresh Token
   * @param ttl 보상 폐기 표시 유지기간
   * @return 현재 토큰이 일치해 폐기되었다면 true
   */
  public boolean revokeIfCurrentMatches(long sessionId, String newlyIssuedToken, Duration ttl) {
    return redisStringClient.revokeIfCurrentMatches(
        key(sessionId), hash(newlyIssuedToken), REVOKED_VALUE, ttl);
  }

  /**
   * DB 트랜잭션 실패 시 Redis에 저장된 신규 토큰을 기존 토큰으로 복원한다.
   *
   * @param sessionId 로그인 세션 ID
   * @param previousToken 기존 Refresh Token 원문
   * @param newlyIssuedToken 새로 발급한 Refresh Token 원문
   * @param previousExpiresAt DB 갱신 전 로그인 세션 만료 시각
   * @return 조건부 복원에 성공하면 true
   */
  public boolean restorePreviousIfCurrentMatches(
      long sessionId,
      String previousToken,
      String newlyIssuedToken,
      LocalDateTime previousExpiresAt) {
    Duration ttl = Duration.between(clock.instant(), previousExpiresAt.toInstant(ZoneOffset.UTC));

    if (ttl.toMillis() < 1) {
      return false;
    }

    return redisStringClient.restorePreviousIfCurrentMatches(
        key(sessionId), hash(newlyIssuedToken), hash(previousToken), ttl);
  }

  /**
   * 로그인 세션 ID와 암호학적으로 안전한 난수로 Refresh Token을 생성한다.
   *
   * @param sessionId 토큰에 연결할 로그인 세션 ID
   * @return 세션 ID와 URL-safe Base64 난수로 구성된 Refresh Token
   */
  private String newToken(long sessionId) {
    byte[] randomBytes = new byte[RANDOM_BYTES];
    secureRandom.nextBytes(randomBytes);

    return sessionId + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
  }

  /**
   * 로그인 세션에 대응하는 Redis Refresh Token 키를 생성한다.
   *
   * @param sessionId 로그인 세션 ID
   * @return 세션별 Redis 키
   */
  private static String key(long sessionId) {
    return KEY_PREFIX + sessionId;
  }

  /**
   * Refresh Token 원문을 SHA-256 해시 문자열로 변환한다.
   *
   * <p>Redis에는 토큰 원문 대신 해시값을 저장한다.
   *
   * @param token 해시할 Refresh Token 원문
   * @return SHA-256 해시의 16진수 문자열
   * @throws IllegalStateException SHA-256 알고리즘을 사용할 수 없는 경우
   */
  private static String hash(String token) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
    }
  }

  /** Refresh Token 교체 및 재사용 여부를 나타내는 상태. */
  public enum RotationStatus {

    /** 현재 Refresh Token 검증에 성공하여 신규 토큰으로 교체한 상태. */
    ROTATED,

    /** 직전 Refresh Token이 교체 후 10초 이내에 재사용되어 재발급만 거부하는 상태. */
    PREVIOUS_WITHIN_GRACE,

    /** 직전 Refresh Token이 교체 후 10초를 초과하여 재사용되어 세션 폐기 대상이 된 상태. */
    PREVIOUS_AFTER_GRACE,

    /** 현재 Refresh Token은 유효하지만 사용자 계정이 비활성화되어 재발급이 금지된 상태. */
    FORBIDDEN,

    /** Refresh Token이 없거나 현재 토큰 및 직전 토큰과 일치하지 않는 상태. */
    INVALID
  }

  /**
   * 원자적 교체 결과.
   *
   * @param status 교체·재사용 판정
   * @param newToken 교체 성공 시 새 Refresh Token, 그 외 null
   */
  public record Rotation(RotationStatus status, String newToken) {}
}
