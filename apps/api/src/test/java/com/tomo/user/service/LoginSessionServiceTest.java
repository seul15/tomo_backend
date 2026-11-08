package com.tomo.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.tomo.user.entity.AccountStatus;
import com.tomo.user.entity.LoginSession;
import com.tomo.user.entity.User;
import com.tomo.user.repository.LoginSessionRepository;
import com.tomo.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoginSessionServiceTest {
  private static final String USER_ID = "user-id";
  private static final long SESSION_ID = 10L;
  private static final Instant NOW = Instant.parse("2026-11-06T00:00:00Z");

  @Mock private UserRepository userRepository;
  @Mock private LoginSessionRepository loginSessionRepository;
  @Mock private User user;
  @Mock private LoginSession session;

  private LoginSessionService service;

  @BeforeEach
  void setUp() {
    service =
        new LoginSessionService(
            userRepository, loginSessionRepository, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  @DisplayName("활성 사용자 본인의 만료되지 않은 세션은 유효하다")
  void acceptsValidSession() {
    // given
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    when(user.getAccountStatus()).thenReturn(AccountStatus.ACTIVE);
    when(loginSessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
    when(session.getUserId()).thenReturn(USER_ID);
    when(session.getExpiresAt())
        .thenReturn(LocalDateTime.ofInstant(NOW.plusSeconds(60), ZoneOffset.UTC));

    // when
    boolean valid = service.isValidAccessSession(USER_ID, SESSION_ID);

    // then
    assertThat(valid).isTrue();
  }

  @Test
  @DisplayName("가입 대기 사용자는 일반 Access 인증을 할 수 없다")
  void rejectsPendingUser() {
    // given
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    when(user.getAccountStatus()).thenReturn(AccountStatus.PENDING);

    // when
    boolean valid = service.isValidAccessSession(USER_ID, SESSION_ID);

    // then
    assertThat(valid).isFalse();
  }

  @Test
  @DisplayName("다른 사용자에게 속한 로그인 세션은 사용할 수 없다")
  void rejectsOtherUsersSession() {
    // given
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    when(user.getAccountStatus()).thenReturn(AccountStatus.ACTIVE);
    when(loginSessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
    when(session.getUserId()).thenReturn("another-user");

    // when
    boolean valid = service.isValidAccessSession(USER_ID, SESSION_ID);

    // then
    assertThat(valid).isFalse();
  }

  @Test
  @DisplayName("만료된 로그인 세션은 사용할 수 없다")
  void rejectsExpiredSession() {
    // given
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    when(user.getAccountStatus()).thenReturn(AccountStatus.ACTIVE);
    when(loginSessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
    when(session.getUserId()).thenReturn(USER_ID);
    when(session.getExpiresAt())
        .thenReturn(LocalDateTime.ofInstant(NOW.minusSeconds(1), ZoneOffset.UTC));

    // when
    boolean valid = service.isValidAccessSession(USER_ID, SESSION_ID);

    // then
    assertThat(valid).isFalse();
  }

  @Test
  @DisplayName("본인 로그인 세션은 삭제할 수 있다")
  void invalidatesOwnedSession() {
    // given
    when(loginSessionRepository.deleteByIdAndUserId(SESSION_ID, USER_ID)).thenReturn(1L);

    // when
    boolean invalidated = service.invalidateOwnedSession(USER_ID, SESSION_ID);

    // then
    assertThat(invalidated).isTrue();
    verify(loginSessionRepository).deleteByIdAndUserId(SESSION_ID, USER_ID);
  }

  @Test
  @DisplayName("다른 사용자에게 속한 로그인 세션은 삭제할 수 없다")
  void rejectsOtherUsersSessionDeletion() {
    // given
    when(loginSessionRepository.deleteByIdAndUserId(SESSION_ID, USER_ID)).thenReturn(0L);

    // when
    boolean invalidated = service.invalidateOwnedSession(USER_ID, SESSION_ID);

    // then
    assertThat(invalidated).isFalse();
    verify(loginSessionRepository).deleteByIdAndUserId(SESSION_ID, USER_ID);
  }

  @Test
  @DisplayName("잘못된 사용자 ID로는 로그인 세션 삭제를 시도하지 않는다")
  void rejectsInvalidUserIdForDeletion() {
    // when
    boolean invalidated = service.invalidateOwnedSession("", SESSION_ID);

    // then
    assertThat(invalidated).isFalse();
    verifyNoInteractions(loginSessionRepository);
  }
}
