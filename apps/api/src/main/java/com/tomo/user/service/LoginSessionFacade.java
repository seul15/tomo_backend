package com.tomo.user.service;

import com.tomo.common.auth.exception.AuthErrorCode;
import com.tomo.common.auth.token.AccessTokenProvider;
import com.tomo.common.exception.BusinessException;
import com.tomo.user.entity.LoginSession;
import com.tomo.user.service.model.TokenRefreshResult;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** DB 로그인 세션과 Redis Refresh Token을 조정한다. */
@Service
public final class LoginSessionFacade {
  private static final Logger LOG = LoggerFactory.getLogger(LoginSessionFacade.class);
  private static final String SESSION_LOCK_PREFIX = "auth:login-session:lock:";
  private static final long LOCK_WAIT_SECONDS = 5L;

  private final LoginSessionService loginSessionService;
  private final RefreshTokenService refreshTokenService;
  private final AccessTokenProvider accessTokenProvider;
  private final Clock clock;
  private final TransactionTemplate transactionTemplate;
  private final Duration accessTokenTtl;
  private final Duration refreshTokenTtl;
  private final RedissonClient redissonClient;

  /**
   * 세션·토큰 조정에 필요한 의존성과 토큰 만료 정책을 주입한다.
   *
   * @param loginSessionService DB 로그인 세션 서비스
   * @param refreshTokenService Redis Refresh Token 서비스
   * @param accessTokenProvider Access JWT 발급기
   * @param clock UTC 기준 시계
   * @param transactionManager DB 트랜잭션 매니저
   * @param redissonClient 로그인 세션별 분산 락 클라이언트
   * @param accessTokenTtl Access Token 유효기간
   * @param refreshTokenTtl Refresh Token 및 로그인 세션 유효기간
   * @throws IllegalArgumentException 토큰 유효기간이 0 이하인 경우
   */
  public LoginSessionFacade(
      LoginSessionService loginSessionService,
      RefreshTokenService refreshTokenService,
      AccessTokenProvider accessTokenProvider,
      Clock clock,
      PlatformTransactionManager transactionManager,
      RedissonClient redissonClient,
      @Value("${tomo.auth.access-token-expiration}") Duration accessTokenTtl,
      @Value("${tomo.auth.refresh-token-expiration}") Duration refreshTokenTtl) {
    this.loginSessionService = loginSessionService;
    this.refreshTokenService = refreshTokenService;
    this.accessTokenProvider = accessTokenProvider;
    this.clock = clock;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
    if (accessTokenTtl.isNegative()
        || accessTokenTtl.isZero()
        || refreshTokenTtl.isNegative()
        || refreshTokenTtl.isZero()) {
      throw new IllegalArgumentException("토큰 유효기간은 양수여야 합니다.");
    }
    this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.accessTokenTtl = accessTokenTtl;
    this.refreshTokenTtl = refreshTokenTtl;
    this.redissonClient = redissonClient;
  }

  /**
   * 기존 Refresh Token을 확인하고 새 Access/Refresh Token을 교체 발급한다.
   *
   * <p>동일 세션의 로그아웃과 재발급은 분산 락으로 직렬화한다. Redis 호출은 DB 트랜잭션 밖에서 수행한다.
   *
   * @param refreshToken 요청에 포함된 Refresh Token
   * @return 새 토큰 쌍과 만료기간
   * @throws BusinessException 유효하지 않거나 사용할 수 없는 토큰인 경우
   * @throws IllegalStateException 분산 락 또는 저장소 조정에 실패한 경우
   */
  public TokenRefreshResult refresh(String refreshToken) {
    Instant requestStartedAt = clock.instant();
    long sessionId = refreshTokenService.extractSessionId(refreshToken);
    return withSessionLock(
        sessionId, () -> refreshUnderLock(sessionId, refreshToken, requestStartedAt));
  }

  /**
   * 세션별 분산 락이 확보된 상태에서 Refresh Token을 검증하고 교체한다.
   *
   * @param sessionId 로그인 세션 ID
   * @param refreshToken 요청 Refresh Token
   * @param requestStartedAt 요청 시작 시각
   * @return 새 Access/Refresh Token 쌍
   */
  private TokenRefreshResult refreshUnderLock(
      long sessionId, String refreshToken, Instant requestStartedAt) {
    RefreshSessionSnapshot snapshot =
        transactionTemplate.execute(
            status -> {
              LoginSession session = loginSessionService.requireRefreshSessionForUpdate(sessionId);
              String userId = session.getUserId();
              return new RefreshSessionSnapshot(
                  userId, session.getExpiresAt(), loginSessionService.isActiveUser(userId));
            });
    if (snapshot == null) {
      throw new IllegalStateException("Refresh Token 세션 조회 결과가 없습니다.");
    }

    Instant now = clock.instant();
    RefreshTokenService.Rotation rotated =
        refreshTokenService.rotate(refreshToken, sessionId, refreshTokenTtl, snapshot.activeUser());

    if (rotated.status() == RefreshTokenService.RotationStatus.ROTATED) {
      try {
        String accessToken =
            accessTokenProvider.issue(snapshot.userId(), sessionId, now.plus(accessTokenTtl));
        LocalDateTime nextExpiresAt =
            LocalDateTime.ofInstant(now.plus(refreshTokenTtl), ZoneOffset.UTC);

        transactionTemplate.executeWithoutResult(
            status -> {
              LoginSession latest = loginSessionService.requireRefreshSessionForUpdate(sessionId);
              if (!snapshot.userId().equals(latest.getUserId())
                  || !loginSessionService.isActiveUser(snapshot.userId())) {
                throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
              }
              loginSessionService.extendExpiration(sessionId, nextExpiresAt);
            });

        return new TokenRefreshResult(
            accessToken,
            rotated.newToken(),
            accessTokenTtl.toSeconds(),
            refreshTokenTtl.toSeconds());
      } catch (RuntimeException exception) {
        compensate(sessionId, refreshToken, rotated.newToken(), snapshot.expiresAt(), exception);
        throw exception;
      }
    }

    if (rotated.status() == RefreshTokenService.RotationStatus.PREVIOUS_AFTER_GRACE) {
      Duration waitingTime = Duration.between(requestStartedAt, now);
      if (waitingTime.compareTo(RefreshTokenService.REUSE_GRACE) >= 0) {
        LOG.warn("처리 지연으로 직전 토큰 폐기 보류. sessionId={}", sessionId);
        throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
      }

      LocalDateTime deletedExpiresAt =
          transactionTemplate.execute(
              status -> {
                LoginSession latest = loginSessionService.requireRefreshSessionForUpdate(sessionId);
                if (!snapshot.userId().equals(latest.getUserId())) {
                  throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
                }
                LocalDateTime oldExpiration = latest.getExpiresAt();
                if (!loginSessionService.invalidateOwnedSession(snapshot.userId(), sessionId)) {
                  throw new IllegalStateException("재사용 감지 후 세션 삭제 실패");
                }
                return oldExpiration;
              });
      if (deletedExpiresAt == null) {
        throw new IllegalStateException("세션 삭제 결과가 없습니다.");
      }
      revokeAfterCommittedDeletion(sessionId, deletedExpiresAt);
      throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
    }

    if (rotated.status() == RefreshTokenService.RotationStatus.FORBIDDEN) {
      throw new BusinessException(AuthErrorCode.TOKEN_REFRESH_FORBIDDEN);
    }
    throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
  }

  /**
   * 현재 사용자 소유의 로그인 세션을 DB에서 삭제하고 Redis 토큰을 폐기한다.
   *
   * @param userId 인증된 사용자 ID
   * @param sessionId JWT에 포함된 로그인 세션 ID
   * @throws BusinessException 소유권 또는 인증 정보가 유효하지 않은 경우
   * @throws IllegalStateException 분산 락 또는 세션 삭제에 실패한 경우
   */
  public void logout(String userId, long sessionId) {
    withSessionLock(
        sessionId,
        () -> {
          LocalDateTime expiresAt =
              transactionTemplate.execute(
                  status -> {
                    LoginSession session =
                        loginSessionService.requireOwnedSession(userId, sessionId);
                    LocalDateTime expiration = session.getExpiresAt();
                    if (!loginSessionService.invalidateOwnedSession(userId, sessionId)) {
                      throw new IllegalStateException("로그아웃 세션 삭제에 실패했습니다.");
                    }
                    return expiration;
                  });
          if (expiresAt == null) {
            throw new IllegalStateException("로그아웃 트랜잭션 결과가 없습니다.");
          }
          revokeAfterCommittedDeletion(sessionId, expiresAt);
          LOG.info("로그아웃 완료. sessionId={}", sessionId);
          return null;
        });
  }

  /**
   * DB 세션 삭제가 커밋된 후 Redis의 Refresh Token을 폐기한다.
   *
   * <p>Redis 폐기에 실패하더라도 DB 세션이 없으므로 인증은 거부된다.
   *
   * @param sessionId 삭제된 로그인 세션 ID
   * @param expiresAt 삭제 전 로그인 세션의 만료 시각
   */
  private void revokeAfterCommittedDeletion(long sessionId, LocalDateTime expiresAt) {
    try {
      refreshTokenService.revokeBeforeSessionDeletion(sessionId, expiresAt);
    } catch (RuntimeException exception) {
      LOG.error("DB 세션 삭제 후 Redis Refresh Token 폐기 실패. sessionId={}", sessionId, exception);
    }
  }

  /**
   * 재발급 트랜잭션 실패 시 Redis 토큰 상태를 이전 값으로 복원한다.
   *
   * <p>현재 토큰과 직전 토큰의 해시가 예상값에 모두 일치할 때만 복원한다.
   *
   * @param sessionId 로그인 세션 ID
   * @param previousToken 기존 Refresh Token
   * @param newlyRotatedToken 교체된 신규 Refresh Token
   * @param previousExpiresAt 갱신 전 DB 세션 만료 시각
   * @param original 재발급 중 발생한 원래 예외
   */
  private void compensate(
      long sessionId,
      String previousToken,
      String newlyRotatedToken,
      LocalDateTime previousExpiresAt,
      RuntimeException original) {
    try {
      boolean restored =
          refreshTokenService.restorePreviousIfCurrentMatches(
              sessionId, previousToken, newlyRotatedToken, previousExpiresAt);

      if (!restored) {
        LOG.error("재발급 실패 후 Redis 토큰 복원 미적용. sessionId={}", sessionId);
      }
    } catch (RuntimeException compensationFailure) {
      LOG.error("Redis 토큰 복원 실패. sessionId={}", sessionId, compensationFailure);
      original.addSuppressed(compensationFailure);
    }
  }

  /**
   * 동일 로그인 세션의 재발급과 로그아웃을 분산 락으로 직렬화한다.
   *
   * @param sessionId 로그인 세션 ID
   * @param work 락을 획득한 상태에서 실행할 작업
   * @param <T> 작업 결과 타입
   * @return 작업 실행 결과
   */
  private <T> T withSessionLock(long sessionId, Supplier<T> work) {
    RLock lock = redissonClient.getLock(SESSION_LOCK_PREFIX + sessionId);
    boolean acquired;
    try {
      // leaseTime을 지정하지 않아서 Redisson의 watchdog 갱신이 사용된다.
      acquired = lock.tryLock(LOCK_WAIT_SECONDS, TimeUnit.SECONDS);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("로그인 세션 락 대기가 중단되었습니다.", exception);
    }
    if (!acquired) {
      throw new BusinessException(AuthErrorCode.SESSION_STATE_CONFLICT);
    }
    try {
      return work.get();
    } finally {
      if (lock.isHeldByCurrentThread()) {
        lock.unlock();
      } else {
        LOG.error("로그인 세션 락 소유권 상실. sessionId={}", sessionId);
      }
    }
  }

  /**
   * 첫 번째 DB 트랜잭션에서 조회한 로그인 세션 정보.
   *
   * @param userId 세션 소유자 ID
   * @param expiresAt 기존 세션 만료 시각
   * @param activeUser ACTIVE 계정 여부
   */
  private record RefreshSessionSnapshot(
      String userId, LocalDateTime expiresAt, boolean activeUser) {}
}
