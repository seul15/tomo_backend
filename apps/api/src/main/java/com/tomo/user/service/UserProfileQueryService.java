package com.tomo.user.service;

import com.tomo.common.exception.BusinessException;
import com.tomo.common.exception.CommonErrorCode;
import com.tomo.user.entity.AccountStatus;
import com.tomo.user.entity.SocialIdentity;
import com.tomo.user.entity.User;
import com.tomo.user.exception.UserErrorCode;
import com.tomo.user.repository.SocialIdentityRepository;
import com.tomo.user.repository.UserRepository;
import com.tomo.user.service.model.UserProfileResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 사용자의 계정 및 프로필 정보를 조회한다. */
@Service
public class UserProfileQueryService {

  private final UserRepository userRepository;
  private final SocialIdentityRepository socialIdentityRepository;

  /**
   * 프로필 조회에 필요한 Repository를 주입받는다.
   *
   * @param userRepository 사용자 저장소
   * @param socialIdentityRepository 소셜 계정 저장소
   */
  public UserProfileQueryService(
      UserRepository userRepository, SocialIdentityRepository socialIdentityRepository) {
    this.userRepository = userRepository;
    this.socialIdentityRepository = socialIdentityRepository;
  }

  /**
   * 인증된 사용자의 계정 및 프로필을 조회한다.
   *
   * @param userId 인증된 사용자 ID
   * @return 프로필 조회 결과
   * @throws BusinessException 사용자 정보가 없거나 조회 권한이 없는 경우
   */
  @Transactional(readOnly = true)
  public UserProfileResult getMyProfile(String userId) {
    if (userId == null || userId.isBlank()) {
      throw new BusinessException(CommonErrorCode.INVALID_ACCESS_TOKEN);
    }

    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));

    if (user.getAccountStatus() != AccountStatus.ACTIVE) {
      throw new BusinessException(UserErrorCode.PROFILE_VIEW_FORBIDDEN);
    }

    SocialIdentity socialIdentity =
        socialIdentityRepository
            .findByUserId(userId)
            .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));

    return new UserProfileResult(
        user.getNickname(),
        user.getProfileImage(),
        socialIdentity.getEmail(),
        socialIdentity.getProvider());
  }
}
