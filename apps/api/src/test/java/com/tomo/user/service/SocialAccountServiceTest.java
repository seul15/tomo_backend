package com.tomo.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.tomo.common.auth.exception.AuthErrorCode;
import com.tomo.common.exception.BusinessException;
import com.tomo.common.exception.CommonErrorCode;
import com.tomo.user.entity.AccountStatus;
import com.tomo.user.entity.SocialIdentity;
import com.tomo.user.entity.SocialProvider;
import com.tomo.user.entity.User;
import com.tomo.user.repository.SocialIdentityRepository;
import com.tomo.user.repository.UserRepository;
import com.tomo.user.service.SocialAccountService.Account;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class SocialAccountServiceTest {
  @Mock private UserRepository users;
  @Mock private SocialIdentityRepository identities;
  @Mock private User user;
  @Mock private SocialIdentity identity;

  @Test
  @DisplayName("기존 ACTIVE 회원은 새 사용자 생성 없이 원래 ID를 반환한다")
  void existingActiveIsReused() {
    // given
    when(identities.findByProviderAndProviderSubject(SocialProvider.GOOGLE, "google-1"))
        .thenReturn(Optional.of(identity));
    when(identity.getUserId()).thenReturn("user-1");
    when(users.findByIdForUpdate("user-1")).thenReturn(Optional.of(user));
    when(user.getAccountStatus()).thenReturn(AccountStatus.ACTIVE);
    when(user.getId()).thenReturn("user-1");
    SocialAccountService service = new SocialAccountService(users, identities);

    // when
    Account result = service.findOrCreate(SocialProvider.GOOGLE, "google-1");

    // then
    assertThat(result.userId()).isEqualTo("user-1");
    assertThat(result.status()).isEqualTo(AccountStatus.ACTIVE);
    verify(users).findByIdForUpdate("user-1");
  }

  @Test
  @DisplayName("BAN 회원은 새 계정이나 세션을 만들 수 없다")
  void bannedIsRejected() {
    // given
    when(identities.findByProviderAndProviderSubject(SocialProvider.KAKAO, "42"))
        .thenReturn(Optional.of(identity));
    when(identity.getUserId()).thenReturn("user-1");
    when(users.findByIdForUpdate("user-1")).thenReturn(Optional.of(user));
    when(user.getAccountStatus()).thenReturn(AccountStatus.BAN);
    SocialAccountService service = new SocialAccountService(users, identities);

    // when
    // then
    assertThatThrownBy(() -> service.findOrCreate(SocialProvider.KAKAO, "42"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex -> assertThat(ex.getErrorCode()).isEqualTo(AuthErrorCode.SOCIAL_LOGIN_FORBIDDEN));
  }

  @Test
  @DisplayName("탈퇴 계정의 소셜 연결은 해제하고 새 PENDING 사용자 ID를 발급한다")
  void deletedAccountCreatesNewUser() {
    // given
    when(identities.findByProviderAndProviderSubject(SocialProvider.KAKAO, "42"))
        .thenReturn(Optional.of(identity));
    when(identity.getUserId()).thenReturn("old-id");
    when(users.findByIdForUpdate("old-id")).thenReturn(Optional.of(user));
    when(user.getAccountStatus()).thenReturn(AccountStatus.DELETE);
    User pending = org.mockito.Mockito.mock(User.class);
    when(pending.getId()).thenReturn("new-id");
    when(users.saveAndFlush(any(User.class))).thenReturn(pending);
    SocialAccountService service = new SocialAccountService(users, identities);
    when(identity.getId()).thenReturn(1L);

    when(identities.findByProviderAndProviderSubjectForUpdate(SocialProvider.KAKAO, "42"))
        .thenReturn(Optional.of(identity));

    // when
    Account result = service.findOrCreate(SocialProvider.KAKAO, "42");

    // then
    assertThat(result.userId()).isEqualTo("new-id");
    assertThat(result.status()).isEqualTo(AccountStatus.PENDING);
    verify(identities).delete(identity);
    verify(identities).flush();
  }

  @Test
  @DisplayName("중복 소셜 연결 INSERT는 409로 분류한다")
  void duplicateSubjectIsConflict() {
    // given
    when(identities.findByProviderAndProviderSubject(SocialProvider.KAKAO, "42"))
        .thenReturn(Optional.empty());
    User pending = org.mockito.Mockito.mock(User.class);
    when(pending.getId()).thenReturn("new-id");
    when(users.saveAndFlush(any(User.class))).thenReturn(pending);
    when(identities.saveAndFlush(any(SocialIdentity.class)))
        .thenThrow(
            new DataIntegrityViolationException(
                "duplicate",
                new SQLIntegrityConstraintViolationException("duplicate", "23000", 1062)));
    SocialAccountService service = new SocialAccountService(users, identities);

    // when
    // then
    assertThatThrownBy(() -> service.findOrCreate(SocialProvider.KAKAO, "42"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex ->
                assertThat(ex.getErrorCode())
                    .isEqualTo(CommonErrorCode.CONCURRENT_REQUEST_CONFLICT));
  }

  @Test
  @DisplayName("비정상적인 제공자 고유 ID는 DB 접근 전에 차단한다")
  void invalidSubjectCannotCreateAccount() {
    // given
    SocialAccountService service = new SocialAccountService(users, identities);

    // when
    // then
    assertThatThrownBy(() -> service.findOrCreate(SocialProvider.GOOGLE, "x".repeat(256)))
        .isInstanceOf(BusinessException.class);
    verifyNoInteractions(users, identities);
  }
}
