package com.tomo.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.tomo.common.exception.BusinessException;
import com.tomo.common.exception.CommonErrorCode;
import com.tomo.user.entity.AccountStatus;
import com.tomo.user.entity.SocialIdentity;
import com.tomo.user.entity.SocialProvider;
import com.tomo.user.repository.SocialIdentityRepository;
import com.tomo.user.repository.UserRepository;
import com.tomo.user.service.SocialAccountService.Account;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(SocialAccountService.class)
class SocialAccountServiceIntegrationTest {
  @Autowired private SocialAccountService service;
  @Autowired private UserRepository users;
  @Autowired private SocialIdentityRepository identities;
  @Autowired private JdbcTemplate jdbcTemplate;

  private final List<String> deletedUserIds = new ArrayList<>();
  private final List<String> createdSubjects = new ArrayList<>();

  @AfterEach
  void cleanup() {
    for (String subject : createdSubjects) {
      identities
          .findByProviderAndProviderSubject(SocialProvider.GOOGLE, subject)
          .ifPresent(
              identity -> {
                String userId = identity.getUserId();
                identities.delete(identity);
                identities.flush();
                users.deleteById(userId);
              });
    }
    for (String deletedUserId : deletedUserIds) {
      users.deleteById(deletedUserId);
    }
  }

  @Test
  @DisplayName("같은 Google 고유 ID로 재로그인해도 PENDING 회원 ID가 변하지 않는다")
  void reusesSamePendingAccount() {
    // given
    String subject = "google-" + UUID.randomUUID();
    createdSubjects.add(subject);

    // when
    Account first = service.findOrCreate(SocialProvider.GOOGLE, subject);
    Account second = service.findOrCreate(SocialProvider.GOOGLE, subject);

    // then
    assertThat(first.userId()).isEqualTo(second.userId());
    assertThat(second.status()).isEqualTo(AccountStatus.PENDING);
    assertThat(identities.findByProviderAndProviderSubject(SocialProvider.GOOGLE, subject))
        .map(SocialIdentity::getUserId)
        .contains(first.userId());
  }

  @Test
  @DisplayName("동일 신규 소셜 계정의 동시 로그인에서 중복 회원 연결을 생성하지 않는다")
  void competingNewLoginsCreateOnlyOneUser() throws Exception {
    // given
    String subject = "google-" + UUID.randomUUID();
    createdSubjects.add(subject);
    long usersBefore = users.count();
    ExecutorService executor = Executors.newFixedThreadPool(2);
    CountDownLatch start = new CountDownLatch(1);
    try {
      Future<Object> first = executor.submit(() -> executeLogin(start, subject));
      Future<Object> second = executor.submit(() -> executeLogin(start, subject));

      // when
      start.countDown();
      Object firstResult = first.get(20, TimeUnit.SECONDS);
      Object secondResult = second.get(20, TimeUnit.SECONDS);

      // then
      String storedUserId =
          identities
              .findByProviderAndProviderSubject(SocialProvider.GOOGLE, subject)
              .orElseThrow()
              .getUserId();
      assertThat(List.of(firstResult, secondResult))
          .allSatisfy(
              result -> {
                if (result instanceof Account account) {
                  assertThat(account.userId()).isEqualTo(storedUserId);
                } else {
                  assertThat(result).isEqualTo(CommonErrorCode.CONCURRENT_REQUEST_CONFLICT);
                }
              });
      assertThat(users.count()).isEqualTo(usersBefore + 1);
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  @DisplayName("DELETE 계정은 기존 ID를 재사용하지 않고 새 PENDING 계정을 생성한다")
  void deletedAccountIsNotReused() {
    // given
    String subject = "google-" + UUID.randomUUID();
    createdSubjects.add(subject);

    Account previous = service.findOrCreate(SocialProvider.GOOGLE, subject);

    deletedUserIds.add(previous.userId());

    jdbcTemplate.update(
        "update users set account_status = 'DELETE' where users_id = ?", previous.userId());

    // when
    Account recreated = service.findOrCreate(SocialProvider.GOOGLE, subject);

    // then
    assertThat(recreated.userId()).isNotEqualTo(previous.userId());
    assertThat(recreated.status()).isEqualTo(AccountStatus.PENDING);

    assertThat(identities.findByProviderAndProviderSubject(SocialProvider.GOOGLE, subject))
        .map(SocialIdentity::getUserId)
        .contains(recreated.userId());
  }

  @Test
  @DisplayName("DELETE 계정의 동시 재가입은 새 소셜 연결 하나만 생성한다")
  void competingDeletedLoginsCreateOnlyOneNewAccount() throws Exception {
    // given
    String subject = "google-" + UUID.randomUUID();
    createdSubjects.add(subject);

    Account previous = service.findOrCreate(SocialProvider.GOOGLE, subject);
    deletedUserIds.add(previous.userId());

    jdbcTemplate.update(
        "update users set account_status = 'DELETE' where users_id = ?", previous.userId());

    long usersBefore = users.count();

    ExecutorService executor = Executors.newFixedThreadPool(2);
    CountDownLatch start = new CountDownLatch(1);

    try {
      Future<Object> first = executor.submit(() -> executeLogin(start, subject));
      Future<Object> second = executor.submit(() -> executeLogin(start, subject));

      // when
      start.countDown();

      Object firstResult = first.get(20, TimeUnit.SECONDS);
      Object secondResult = second.get(20, TimeUnit.SECONDS);

      // then
      String newUserId =
          identities
              .findByProviderAndProviderSubject(SocialProvider.GOOGLE, subject)
              .orElseThrow()
              .getUserId();

      assertThat(newUserId).isNotEqualTo(previous.userId());

      assertThat(List.of(firstResult, secondResult))
          .filteredOn(result -> result instanceof Account)
          .isNotEmpty();

      assertThat(List.of(firstResult, secondResult))
          .allSatisfy(
              result -> {
                if (result instanceof Account account) {
                  assertThat(account.userId()).isEqualTo(newUserId);
                  assertThat(account.status()).isEqualTo(AccountStatus.PENDING);
                } else {
                  assertThat(result).isEqualTo(CommonErrorCode.CONCURRENT_REQUEST_CONFLICT);
                }
              });

      assertThat(users.count()).isEqualTo(usersBefore + 1);
    } finally {
      executor.shutdownNow();
    }
  }

  private Object executeLogin(CountDownLatch start, String subject) throws Exception {
    start.await();
    try {
      return service.findOrCreate(SocialProvider.GOOGLE, subject);
    } catch (BusinessException exception) {
      return exception.getErrorCode();
    }
  }
}
