package com.tomo.user.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.tomo.user.entity.AccountStatus;
import com.tomo.user.entity.AgreementType;
import com.tomo.user.entity.DocumentPublicationStatus;
import com.tomo.user.entity.DocumentVersion;
import com.tomo.user.entity.LoginSession;
import com.tomo.user.entity.SocialIdentity;
import com.tomo.user.entity.SocialProvider;
import com.tomo.user.entity.User;
import com.tomo.user.entity.UserAgreement;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserPersistenceTest {
  @Autowired private EntityManager entityManager;

  @Autowired private UserRepository userRepository;

  @Autowired private SocialIdentityRepository socialIdentityRepository;

  @Autowired private LoginSessionRepository loginSessionRepository;

  @Autowired private DocumentVersionRepository documentVersionRepository;

  @Autowired private UserAgreementRepository userAgreementRepository;

  @Test
  @DisplayName("신규 사용자는 UUID를 발급받고 가입 대기 상태로 저장된다")
  void savesPendingUser() {
    // given
    User user = User.createPending();

    // when
    userRepository.saveAndFlush(user);
    entityManager.clear();

    // then
    assertThat(UUID.fromString(user.getId())).isNotNull();
    assertThat(userRepository.findById(user.getId()))
        .get()
        .extracting(User::getAccountStatus)
        .isEqualTo(AccountStatus.PENDING);
  }

  @Test
  @DisplayName("제공자와 식별값으로 연결된 소셜 계정을 조회한다")
  void findsSocialIdentity() {
    // given
    User user = userRepository.saveAndFlush(User.createPending());
    socialIdentityRepository.saveAndFlush(
        SocialIdentity.link(user.getId(), SocialProvider.GOOGLE, "google-subject", null));

    // when
    entityManager.clear();
    Optional<SocialIdentity> result =
        socialIdentityRepository.findByProviderAndProviderSubject(
            SocialProvider.GOOGLE, "google-subject");

    // then
    assertThat(result).isPresent();
    assertThat(result.orElseThrow().getUserId()).isEqualTo(user.getId());
  }

  @Test
  @DisplayName("하나의 사용자는 여러 기기의 로그인 세션을 가질 수 있다")
  void supportsMultipleSessions() {
    // given
    User user = userRepository.saveAndFlush(User.createPending());
    LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

    // when
    loginSessionRepository.saveAndFlush(LoginSession.create(user.getId(), now, now.plusHours(1)));
    loginSessionRepository.saveAndFlush(LoginSession.create(user.getId(), now, now.plusHours(1)));

    entityManager.clear();

    // then
    assertThat(loginSessionRepository.findByUserId(user.getId())).hasSize(2);
  }

  @Test
  @DisplayName("약관 문서 버전과 사용자 동의 이력은 기존 데이터 모델로 저장된다")
  void savesAgreementWithDocumentVersion() {
    // given
    User user = userRepository.saveAndFlush(User.createPending());
    DocumentVersion document =
        documentVersionRepository.saveAndFlush(
            DocumentVersion.create(
                AgreementType.TERMS_AGREED,
                "1.0",
                "테스트 약관 본문",
                LocalDate.of(2026, 11, 1),
                DocumentPublicationStatus.APPROVED));

    // when
    userAgreementRepository.saveAndFlush(
        UserAgreement.record(
            user.getId(),
            AgreementType.TERMS_AGREED,
            document.getId(),
            true,
            LocalDateTime.now(ZoneOffset.UTC)));

    entityManager.clear();

    // then
    assertThat(userAgreementRepository.findByUserId(user.getId()))
        .singleElement()
        .satisfies(
            agreement -> {
              assertThat(agreement.getDocumentVersionId()).isEqualTo(document.getId());
              assertThat(agreement.getAgreedYn()).isEqualTo("1");
            });
  }
}
