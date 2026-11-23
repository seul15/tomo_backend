package com.tomo.user.service;

import com.tomo.common.auth.exception.AuthErrorCode;
import com.tomo.common.exception.BusinessException;
import com.tomo.common.exception.CommonErrorCode;
import com.tomo.user.entity.AccountStatus;
import com.tomo.user.entity.User;
import com.tomo.user.exception.UserErrorCode;
import com.tomo.user.repository.UserRepository;
import com.tomo.user.service.model.UserProfileDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 사용자의 계정 및 프로필 정보를 조회한다. */
@Service
public class UserProfileQueryService {
  private static final Logger LOG = LoggerFactory.getLogger(UserProfileQueryService.class);

  private final UserRepository userRepository;

  /**
   * 사용자 저장소를 주입받는다.
   *
   * @param userRepository 사용자 저장소
   */
  public UserProfileQueryService(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  /**
   * 활성 사용자의 프로필 정보를 조회한다.
   *
   * @param userId 인증된 사용자 ID
   * @return 사용자 프로필 정보
   * @throws BusinessException 사용자 정보가 없거나 조회 권한이 없는 경우
   */
  @Transactional(readOnly = true)
  public UserProfileDetails getActiveUserProfile(String userId) {
    if (userId == null || userId.isBlank()) {
      LOG.debug("프로필 조회 거부. 인증 사용자 ID 없음");
      throw new BusinessException(AuthErrorCode.INVALID_ACCESS_TOKEN);
    }

    User user =
        userRepository
            .findById(userId)
            .orElseThrow(
                () -> {
                  LOG.warn("프로필 조회 중 인증 사용자 정보가 존재하지 않음");
                  return new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND);
                });

    if (user.getAccountStatus() != AccountStatus.ACTIVE) {
      LOG.debug("프로필 조회 거부. accountStatus={}", user.getAccountStatus());
      throw new BusinessException(UserErrorCode.PROFILE_VIEW_FORBIDDEN);
    }

    return new UserProfileDetails(user.getNickname(), user.getProfileImage());
  }
}
