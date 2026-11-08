package com.tomo.user.service;

import com.tomo.common.auth.session.AccessSessionValidator;
import com.tomo.user.entity.AccountStatus;
import com.tomo.user.repository.LoginSessionRepository;
import com.tomo.user.repository.UserRepository;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Access 인증을 위한 세션 유효성 조회와 로그인 세션 무효화를 담당한다. */
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
}
