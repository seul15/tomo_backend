package com.tomo.user.service;

import com.tomo.user.service.model.SocialIdentityDetails;
import com.tomo.user.service.model.UserProfileDetails;
import com.tomo.user.service.model.UserProfileResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 사용자 정보와 소셜 계정 정보를 조합하여 프로필 조회 기능을 제공한다. */
@Service
public class UserProfileFacade {

  private final UserProfileQueryService userProfileQueryService;
  private final SocialIdentityQueryService socialIdentityQueryService;

  /**
   * 프로필 조회 서비스를 주입받는다.
   *
   * @param userProfileQueryService 사용자 프로필 조회 서비스
   * @param socialIdentityQueryService 소셜 계정 조회 서비스
   */
  public UserProfileFacade(
      UserProfileQueryService userProfileQueryService,
      SocialIdentityQueryService socialIdentityQueryService) {
    this.userProfileQueryService = userProfileQueryService;
    this.socialIdentityQueryService = socialIdentityQueryService;
  }

  /**
   * 인증된 사용자의 전체 프로필 정보를 조회한다.
   *
   * @param userId 인증된 사용자 ID
   * @return 전체 프로필 조회 결과
   */
  @Transactional(readOnly = true)
  public UserProfileResult getMyProfile(String userId) {
    UserProfileDetails profile = userProfileQueryService.getActiveUserProfile(userId);

    SocialIdentityDetails social = socialIdentityQueryService.getProfileDetailsByUserId(userId);

    return new UserProfileResult(
        profile.nickname(), profile.profileImageUrl(), social.email(), social.provider());
  }
}
