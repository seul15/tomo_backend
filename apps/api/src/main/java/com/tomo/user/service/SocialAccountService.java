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
   * 소셜 계정을 조회하고, 처음 로그인한 경우 신규 PENDING 계정을 저장한다.
   *
   * @param provider Google 또는 Kakao
   * @param subject 제공자가 검증한 고유 ID
   * @param email 제공자가 제공한 선택 이메일
   * @return 로그인할 회원 상태와 프로필
   * @throws BusinessException 로그인 금지, 식별 정보 오류, 동시 연결 충돌
   */
  @Transactional
  public Account findOrCreate(SocialProvider provider, String subject, String email) {
    if (provider == null
        || subject == null
        || subject.isBlank()
        || subject.length() > 255
        || (email != null && email.length() > 320)) {
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
      // 과거 DELETE 계정의 연결만 해제하며, 사용자 데이터는 변경하거나 승계하지 않는다.
      socialIdentityRepository.delete(identity);
      socialIdentityRepository.flush();
    }

    User pending = userRepository.saveAndFlush(User.createPending());
    try {
      socialIdentityRepository.saveAndFlush(
          SocialIdentity.link(pending.getId(), provider, subject, email));
    } catch (DataIntegrityViolationException exception) {
      // MySQL의 중복키 오류(1062)만 409. FK/기타 무결성 오류는 500으로 처리한다.
      if (isDuplicateKey(exception)) {
        throw new BusinessException(CommonErrorCode.CONCURRENT_REQUEST_CONFLICT);
      }
      throw exception;
    }
    return new Account(pending.getId(), AccountStatus.PENDING, null, null);
  }

  private static boolean isDuplicateKey(Throwable exception) {
    Throwable cause = exception;
    while (cause != null) {
      if (cause instanceof SQLException sql && sql.getErrorCode() == 1062) {
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
