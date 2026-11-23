package com.tomo.user.service;

import com.tomo.common.auth.exception.AuthErrorCode;
import com.tomo.common.auth.session.AccessSessionValidator;
import com.tomo.common.exception.BusinessException;
import com.tomo.common.exception.CommonErrorCode;
import com.tomo.user.entity.AccountStatus;
import com.tomo.user.entity.LoginSession;
import com.tomo.user.repository.LoginSessionRepository;
import com.tomo.user.repository.UserRepository;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Access 인증과 로그인 세션 잠금 조회·만료 연장·무효화를 담당한다. */
@Service
public class LoginSessionService implements AccessSessionValidator {
  private final UserRepository userRepository;
  private final LoginSessionRepository loginSessionRepository;
  private final Clock clock;

  /**
   * 인증 상태 조회에 필요한 의존성을 주입한다.
   *
   * @param userRepository 사용자 저장소
   * @param loginSessionRepository 로그인 세션 저장소
   * @param clock 인증 시각 확인에 사용할 시계
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Spring이 관리하는 JPA Repository를 공유 의존성으로 주입받는다.")
  public LoginSessionService(
      UserRepository userRepository, LoginSessionRepository loginSessionRepository, Clock clock) {
    this.userRepository = userRepository;
    this.loginSessionRepository = loginSessionRepository;
    this.clock = clock;
  }

  /**
   * 활성 사용자에게 속한 만료되지 않은 로그인 세션인지 확인한다.
   *
   * @param userId 사용자 ID
   * @param sessionId 로그인 세션 ID
   * @return 인증에 유효한 사용자와 세션이면 true
   */
  @Override
  @Transactional(readOnly = true)
  public boolean isValidAccessSession(String userId, long sessionId) {
    if (userId == null || userId.isBlank() || sessionId <= 0) {
      return false;
    }

    boolean activeUser =
        userRepository
            .findById(userId)
            .filter(user -> user.getAccountStatus() == AccountStatus.ACTIVE)
            .isPresent();

    if (!activeUser) {
      return false;
    }

    LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);

    return loginSessionRepository
        .findById(sessionId)
        .filter(session -> userId.equals(session.getUserId()))
        .filter(session -> session.getExpiresAt().isAfter(now))
        .isPresent();
  }

  /**
   * 사용자 소유권을 확인하고 로그인 세션을 조회한다.
   *
   * @param userId 사용자 ID
   * @param sessionId 로그인 세션 ID
   * @return 소유권이 확인된 로그인 세션
   * @throws BusinessException 요청 또는 세션 소유권이 유효하지 않은 경우
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public LoginSession requireOwnedSession(String userId, long sessionId) {
    if (userId == null || userId.isBlank() || sessionId <= 0) {
      throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
    }

    LoginSession session =
        loginSessionRepository
            .findByIdForUpdate(sessionId)
            .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_ACCESS_TOKEN));

    if (!userId.equals(session.getUserId())) {
      throw new BusinessException(AuthErrorCode.INVALID_ACCESS_TOKEN);
    }

    LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    if (!session.getExpiresAt().isAfter(now)) {
      throw new BusinessException(AuthErrorCode.INVALID_ACCESS_TOKEN);
    }

    return session;
  }

  /**
   * 지정된 사용자가 소유한 로그인 세션을 삭제한다.
   *
   * @param userId 세션 소유자 ID
   * @param sessionId 로그인 세션 ID
   * @return 해당 사용자의 세션이 삭제되었으면 true
   */
  @Transactional
  public boolean invalidateOwnedSession(String userId, long sessionId) {
    if (userId == null || userId.isBlank() || sessionId <= 0) {
      return false;
    }

    return loginSessionRepository.deleteByIdAndUserId(sessionId, userId) > 0;
  }

  /**
   * Refresh Token이 가리키는 로그인 세션을 쓰기 잠금으로 조회하고 만료 여부를 검사한다.
   *
   * @param sessionId Refresh Token에서 추출한 로그인 세션 ID
   * @return 유효기간 내에 존재하는 로그인 세션
   * @throws BusinessException 세션이 없거나 만료된 경우
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public LoginSession requireRefreshSessionForUpdate(long sessionId) {
    if (sessionId <= 0) {
      throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
    }

    LoginSession session =
        loginSessionRepository
            .findByIdForUpdate(sessionId)
            .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN));

    LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    if (!session.getExpiresAt().isAfter(now)) {
      throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
    }
    return session;
  }

  /**
   * 사용자의 계정이 ACTIVE 상태인지 확인한다. Refresh Token 일치 판정 전에는 상태를 외부로 노출하지 않는다.
   *
   * @param userId 계정 ID
   * @return ACTIVE 사용자이면 true
   */
  @Transactional(readOnly = true)
  public boolean isActiveUser(String userId) {
    return userRepository
        .findById(userId)
        .filter(user -> user.getAccountStatus() == AccountStatus.ACTIVE)
        .isPresent();
  }

  /**
   * 기존 로그인 세션의 만료 시각을 Repository UPDATE로 갱신한다.
   *
   * @param sessionId 잠금 조회된 로그인 세션 ID
   * @param newExpiresAt 새 만료 시각(UTC)
   * @throws BusinessException 갱신할 세션이 존재하지 않는 경우
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public void extendExpiration(long sessionId, LocalDateTime newExpiresAt) {
    if (sessionId <= 0 || newExpiresAt == null) {
      throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
    }
    int updated = loginSessionRepository.updateExpiresAt(sessionId, newExpiresAt);
    if (updated != 1) {
      throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
    }
  }
}
