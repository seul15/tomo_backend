package com.tomo.user.service;

import com.tomo.common.auth.exception.AuthErrorCode;
import com.tomo.common.exception.BusinessException;
import com.tomo.common.exception.CommonErrorCode;
import com.tomo.user.entity.AccountStatus;
import com.tomo.user.entity.SocialIdentity;
import com.tomo.user.entity.SocialProvider;
import com.tomo.user.entity.User;
import com.tomo.user.repository.SocialIdentityRepository;
import com.tomo.user.repository.UserRepository;
import java.sql.SQLException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 소셜 고유 ID 기준으로 기존 계정을 찾거나 신규 가입 대기 계정을 생성한다. */
@Service
public class SocialAccountService {
  private static final int MYSQL_DUPLICATE_KEY_ERROR_CODE = 1062;

  private final UserRepository userRepository;
  private final SocialIdentityRepository socialIdentityRepository;

  /**
   * 회원 및 소셜 연결 저장소를 주입한다.
   *
   * @param userRepository 회원 저장소
   * @param socialIdentityRepository 소셜 연결 저장소
   */
  public SocialAccountService(
      UserRepository userRepository, SocialIdentityRepository socialIdentityRepository) {
    this.userRepository = userRepository;
    this.socialIdentityRepository = socialIdentityRepository;
  }

  /**
   * 소셜 계정을 조회하고, 처음 로그인한 경우 PENDING 계정을 생성한다.
   *
   * <p>DELETE 회원의 연결을 교체할 때는 최신 연결 정보를 재확인한다.
   *
   * @param provider Google 또는 Kakao
   * @param subject 제공자가 검증한 사용자 고유 ID
   * @return 로그인할 회원 상태와 프로필
   * @throws BusinessException 로그인 금지, 식별값 오류 또는 동시 연결 충돌
   */
  @Transactional
  public Account findOrCreate(SocialProvider provider, String subject) {
    if (provider == null || subject == null || subject.isBlank() || subject.length() > 255) {
      throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
    }

    SocialIdentity identity =
        socialIdentityRepository.findByProviderAndProviderSubject(provider, subject).orElse(null);

    if (identity != null) {
      User user =
          userRepository
              .findByIdForUpdate(identity.getUserId())
              .orElseThrow(() -> new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR));

      if (user.getAccountStatus() == AccountStatus.BAN) {
        throw new BusinessException(AuthErrorCode.SOCIAL_LOGIN_FORBIDDEN);
      }

      if (user.getAccountStatus() == AccountStatus.PENDING
          || user.getAccountStatus() == AccountStatus.ACTIVE) {
        return new Account(
            user.getId(), user.getAccountStatus(), user.getNickname(), user.getProfileImage());
      }

      // DELETE 회원의 이전 소셜 연결이 여전히 존재하는지 확인한다.
      SocialIdentity latestIdentity =
          socialIdentityRepository
              .findByProviderAndProviderSubjectForUpdate(provider, subject)
              .orElseThrow(
                  () -> new BusinessException(CommonErrorCode.CONCURRENT_REQUEST_CONFLICT));

      if (identity.getId() == null || !identity.getId().equals(latestIdentity.getId())) {
        throw new BusinessException(CommonErrorCode.CONCURRENT_REQUEST_CONFLICT);
      }

      socialIdentityRepository.delete(latestIdentity);
      socialIdentityRepository.flush();
    }

    User pending = userRepository.saveAndFlush(User.createPending());

    try {
      // 소셜 이메일은 저장하지 않고 제공자 고유 ID만 연결한다.
      socialIdentityRepository.saveAndFlush(
          SocialIdentity.link(pending.getId(), provider, subject, null));

    } catch (DataIntegrityViolationException exception) {
      if (isDuplicateKey(exception)) {
        throw new BusinessException(CommonErrorCode.CONCURRENT_REQUEST_CONFLICT);
      }
      throw exception;
    }

    return new Account(pending.getId(), AccountStatus.PENDING, null, null);
  }

  /**
   * 데이터베이스 예외의 원인 체인에서 MySQL 중복 키 오류를 확인한다.
   *
   * <p>MySQL 오류 번호 1062를 검사해 중복 연결 시도를 동시 요청 충돌로 변환하기 위해 사용한다.
   *
   * @param exception 데이터 무결성 예외
   * @return 중복 키 오류가 확인되면 true
   */
  private static boolean isDuplicateKey(Throwable exception) {
    Throwable cause = exception;
    while (cause != null) {
      if (cause instanceof SQLException sql
          && sql.getErrorCode() == MYSQL_DUPLICATE_KEY_ERROR_CODE) {
        return true;
      }
      cause = cause.getCause();
    }
    return false;
  }

  /**
   * 가입 상태와 현재 프로필을 담는 내부 업무 결과.
   *
   * @param userId 회원 ID
   * @param status 가입 상태
   * @param nickname 가입 완료 닉네임
   * @param profileImageUrl 프로필 이미지 URL
   */
  public record Account(
      String userId, AccountStatus status, String nickname, String profileImageUrl) {}
}
