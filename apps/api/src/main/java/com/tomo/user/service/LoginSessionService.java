package com.tomo.user.service;

import com.tomo.user.entity.AccountStatus;
import com.tomo.user.repository.LoginSessionRepository;
import com.tomo.user.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Access 인증에 사용되는 사용자와 로그인 세션의 유효성을 조회한다. */
@Service
public class LoginSessionService {
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
}
