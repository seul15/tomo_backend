package com.tomo.user.service;

import com.tomo.common.auth.token.AccessTokenProvider;
import com.tomo.common.exception.BusinessException;
import com.tomo.user.entity.LoginSession;
import com.tomo.user.exception.UserErrorCode;
import com.tomo.user.service.model.TokenRefreshResult;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** DB 로그인 세션과 Redis Refresh Token을 조정한다. */
@Service
public final class LoginSessionFacade {
  private static final Logger LOG = LoggerFactory.getLogger(LoginSessionFacade.class);
  private final LoginSessionService loginSessionService;
  private final RefreshTokenService refreshTokenService;
  private final AccessTokenProvider accessTokenProvider;
  private final Clock clock;
  private final TransactionTemplate transactionTemplate;
  private final Duration accessTokenTtl;
  private final Duration refreshTokenTtl;

  /**
   * 세션·토큰 조정에 필요한 의존성과 토큰 만료 정책을 주입한다.
   *
   * @param loginSessionService DB 로그인 세션 서비스
   * @param refreshTokenService Redis Refresh Token 서비스
   * @param accessTokenProvider Access JWT 발급기
   * @param clock UTC 기준 시계
   * @param transactionManager DB 트랜잭션 매니저
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
    this.accessTokenTtl = accessTokenTtl;
    this.refreshTokenTtl = refreshTokenTtl;
  }

  /**
   * 현재 Refresh Token을 검증·교체하고 DB 로그인 세션 만료 시각을 연장한다.
   *
   * <p>DB 커밋에 실패한 경우 Redis 토큰을 조건부 폐기한다. 10초 이후 직전 토큰 재사용은 DB 삭제를 커밋한 후 401로 변환한다.
   *
   * @param refreshToken Authorization 헤더에서 추출한 Refresh Token
   * @return 새 Access Token·Refresh Token과 만료기간
   * @throws BusinessException 재발급이 거부되거나 계정 상태가 유효하지 않은 경우
   */
  public TokenRefreshResult refresh(String refreshToken) {
    long sessionId = refreshTokenService.extractSessionId(refreshToken);
    AtomicReference<String> newlyRotated = new AtomicReference<>();
    RefreshTransactionResult transactionResult;
    try {
      transactionResult =
          transactionTemplate.execute(
              status -> {
                LoginSession session =
                    loginSessionService.requireRefreshSessionForUpdate(sessionId);
                // 한 번 확인한 시각을 새 DB 만료 시각과 토큰 발급에 사용한다.
                Instant now = clock.instant();
                String userId = session.getUserId();
                boolean activeUser = loginSessionService.isActiveUser(userId);
                RefreshTokenService.Rotation rotated =
                    refreshTokenService.rotate(
                        refreshToken, sessionId, refreshTokenTtl, activeUser);
                if (rotated.status() == RefreshTokenService.RotationStatus.ROTATED) {
                  newlyRotated.set(rotated.newToken());
                  LocalDateTime nextExpiresAt =
                      LocalDateTime.ofInstant(now.plus(refreshTokenTtl), ZoneOffset.UTC);
                  loginSessionService.extendExpiration(sessionId, nextExpiresAt);
                  return new RefreshTransactionResult(
                      RefreshStatus.SUCCESS, userId, rotated.newToken());
                }
                if (rotated.status() == RefreshTokenService.RotationStatus.PREVIOUS_AFTER_GRACE) {
                  refreshTokenService.revokeBeforeSessionDeletion(
                      sessionId, session.getExpiresAt());
                  if (!loginSessionService.invalidateOwnedSession(session.getUserId(), sessionId)) {
                    throw new IllegalStateException("재사용 감지 후 로그인 세션 삭제에 실패했습니다.");
                  }
                }
                RefreshStatus statusResult =
                    rotated.status() == RefreshTokenService.RotationStatus.FORBIDDEN
                        ? RefreshStatus.FORBIDDEN
                        : RefreshStatus.INVALID;
                return new RefreshTransactionResult(statusResult, null, null);
              });
    } catch (RuntimeException exception) {
      if (newlyRotated.get() != null) {
        compensate(sessionId, newlyRotated.get(), exception);
      }
      throw exception;
    }
    if (transactionResult == null || transactionResult.status() != RefreshStatus.SUCCESS) {
      if (transactionResult != null && transactionResult.status() == RefreshStatus.FORBIDDEN) {
        throw new BusinessException(UserErrorCode.TOKEN_REFRESH_FORBIDDEN);
      }
      throw new BusinessException(UserErrorCode.INVALID_REFRESH_TOKEN);
    }
    try {
      String access =
          accessTokenProvider.issue(
              transactionResult.userId(), sessionId, clock.instant().plus(accessTokenTtl));
      return new TokenRefreshResult(
          access,
          transactionResult.refreshToken(),
          accessTokenTtl.toSeconds(),
          refreshTokenTtl.toSeconds());
    } catch (RuntimeException exception) {
      compensate(sessionId, transactionResult.refreshToken(), exception);
      throw exception;
    }
  }

  /**
   * Refresh Token 폐기 후 본인 로그인 세션을 삭제한다.
   *
   * @param userId 검증된 사용자 ID
   * @param sessionId 로그인 세션 ID
   * @throws BusinessException 요청 또는 세션 소유권이 유효하지 않은 경우
   */
  public void logout(String userId, long sessionId) {
    transactionTemplate.executeWithoutResult(
        status -> {
          LoginSession session = loginSessionService.requireOwnedSession(userId, sessionId);
          refreshTokenService.revokeBeforeSessionDeletion(sessionId, session.getExpiresAt());
          if (!loginSessionService.invalidateOwnedSession(userId, sessionId)) {
            throw new IllegalStateException("로그아웃 대상 세션을 삭제하지 못했습니다.");
          }
        });
    LOG.info("로그아웃 완료. sessionId={}", sessionId);
  }

  private void compensate(long sessionId, String newlyRotatedToken, RuntimeException original) {
    try {
      boolean revoked =
          refreshTokenService.revokeIfCurrentMatches(sessionId, newlyRotatedToken, refreshTokenTtl);
      if (!revoked) {
        LOG.error("재발급 실패 후 Redis 보상 폐기가 적용되지 않음. sessionId={}", sessionId);
      }
    } catch (RuntimeException compensationFailure) {
      LOG.error("Redis 보상 폐기 실패. sessionId={}", sessionId, compensationFailure);
      original.addSuppressed(compensationFailure);
    }
  }

  private enum RefreshStatus {
    SUCCESS,
    INVALID,
    FORBIDDEN
  }

  private record RefreshTransactionResult(
      RefreshStatus status, String userId, String refreshToken) {}
}
