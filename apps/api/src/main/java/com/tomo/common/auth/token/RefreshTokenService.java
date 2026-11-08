package com.tomo.common.auth.token;

import com.tomo.common.exception.BusinessException;
import com.tomo.common.exception.ErrorCode;
import com.tomo.integration.redis.RedisStringClient;
import com.tomo.user.entity.LoginSession;
import com.tomo.user.repository.LoginSessionRepository;
import com.tomo.user.service.LoginSessionService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/** 로그인 세션에 연결된 Refresh Token의 발급, 검증, 폐기를 담당한다. */
@Service
public class RefreshTokenService {
  private static final String KEY_PREFIX = "auth:refresh:session:";
  private static final int RANDOM_BYTES = 32;
  private static final int MAX_TOKEN_LENGTH = 64;

  private static final Pattern TOKEN_PATTERN =
      Pattern.compile("([1-9][0-9]*)\\.([A-Za-z0-9_-]{43})");

  private final LoginSessionRepository loginSessionRepository;
  private final LoginSessionService loginSessionService;
  private final RedisStringClient redisStringClient;
  private final Clock clock;
  private final SecureRandom secureRandom = new SecureRandom();

  /**
   * Refresh Token 관리에 필요한 의존성을 주입한다.
   *
   * @param loginSessionRepository 로그인 세션 저장소
   * @param loginSessionService 로그인 세션 유효성 검증 서비스
   * @param redisStringClient Redis 문자열 저장 기능
   * @param clock 만료 시각 확인용 시계
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Spring이 관리하는 RedisStringClient를 공유 의존성으로 주입받으며 외부에 노출하지 않는다.")
  public RefreshTokenService(
      LoginSessionRepository loginSessionRepository,
      LoginSessionService loginSessionService,
      RedisStringClient redisStringClient,
      Clock clock) {
    this.loginSessionRepository = loginSessionRepository;
    this.loginSessionService = loginSessionService;
    this.redisStringClient = redisStringClient;
    this.clock = clock;
  }

  /**
   * 기존 로그인 세션에 Refresh Token을 최초 발급한다.
   *
   * <p>DB 로그인 세션의 남은 유효시간을 Redis TTL로 사용한다. 이미 발급한 세션의 토큰은 덮어쓰지 않는다.
   *
   * @param userId 검증된 사용자 ID
   * @param sessionId 로그인 세션 ID
   * @return Refresh Token 원문
   * @throws BusinessException 세션이 유효하지 않거나 이미 토큰이 존재하는 경우
   */
  public String issue(String userId, long sessionId) {
    if (userId == null || userId.isBlank() || sessionId <= 0) {
      throw new BusinessException(ErrorCode.INVALID_REQUEST);
    }

    LoginSession session =
        loginSessionRepository
            .findById(sessionId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

    if (!userId.equals(session.getUserId())) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED);
    }

    Instant expiresAt = session.getExpiresAt().toInstant(ZoneOffset.UTC);
    Duration ttl = Duration.between(clock.instant(), expiresAt);

    if (ttl.isZero() || ttl.isNegative()) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED);
    }

    if (!loginSessionService.isValidAccessSession(userId, sessionId)) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED);
    }

    byte[] randomBytes = new byte[RANDOM_BYTES];
    secureRandom.nextBytes(randomBytes);

    String token =
        sessionId + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

    boolean stored = redisStringClient.setIfAbsent(key(sessionId), hash(token), ttl);

    if (!stored) {
      throw new BusinessException(ErrorCode.RESOURCE_CONFLICT);
    }

    return token;
  }

  /**
   * Refresh Token 및 연결된 로그인 세션의 유효성을 확인한다.
   *
   * <p>검증 과정에서 토큰을 변경하거나 Redis TTL을 연장하지 않는다.
   *
   * @param token Refresh Token 원문
   * @return 유효한 사용자 및 로그인 세션 정보
   */
  public Optional<RefreshTokenValidationResult> findValidSession(String token) {

    long sessionId = parseSessionId(token);

    if (sessionId <= 0) {
      return Optional.empty();
    }

    String storedHash = redisStringClient.get(key(sessionId));

    if (storedHash == null) {
      return Optional.empty();
    }

    byte[] expected = storedHash.getBytes(StandardCharsets.US_ASCII);
    byte[] actual = hash(token).getBytes(StandardCharsets.US_ASCII);

    if (!MessageDigest.isEqual(expected, actual)) {
      return Optional.empty();
    }

    return loginSessionRepository
        .findById(sessionId)
        .filter(session -> loginSessionService.isValidAccessSession(session.getUserId(), sessionId))
        .map(session -> new RefreshTokenValidationResult(session.getUserId(), sessionId));
  }

  /**
   * 특정 로그인 세션의 Refresh Token을 폐기한다.
   *
   * <p>호출 측에서 사용자와 세션의 소유 관계를 검증해야 한다. DB 세션 삭제는 별도 업무 흐름에서 수행한다.
   *
   * @param sessionId 로그인 세션 ID
   * @throws BusinessException 세션 ID가 올바르지 않은 경우
   */
  public void revoke(long sessionId) {
    if (sessionId <= 0) {
      throw new BusinessException(ErrorCode.INVALID_REQUEST);
    }

    redisStringClient.delete(key(sessionId));
  }

  private static String key(long sessionId) {
    return KEY_PREFIX + sessionId;
  }

  private static long parseSessionId(String token) {
    if (token == null || token.length() > MAX_TOKEN_LENGTH) {
      return -1;
    }

    Matcher matcher = TOKEN_PATTERN.matcher(token);

    if (!matcher.matches()) {
      return -1;
    }

    try {
      return Long.parseLong(matcher.group(1));
    } catch (NumberFormatException exception) {
      return -1;
    }
  }

  private static String hash(String token) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));

      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", exception);
    }
  }
}
