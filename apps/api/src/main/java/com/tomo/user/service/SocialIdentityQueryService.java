package com.tomo.user.service;

import com.tomo.common.exception.BusinessException;
import com.tomo.common.exception.CommonErrorCode;
import com.tomo.user.entity.SocialIdentity;
import com.tomo.user.repository.SocialIdentityRepository;
import com.tomo.user.service.model.SocialIdentityDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 소셜 계정 연결 정보를 조회한다. */
@Service
public class SocialIdentityQueryService {
  private static final Logger LOG = LoggerFactory.getLogger(SocialIdentityQueryService.class);

  private final SocialIdentityRepository socialIdentityRepository;

  /**
   * 소셜 계정 저장소를 주입받는다.
   *
   * @param socialIdentityRepository 소셜 계정 저장소
   */
  public SocialIdentityQueryService(SocialIdentityRepository socialIdentityRepository) {
    this.socialIdentityRepository = socialIdentityRepository;
  }

  /**
   * 사용자 ID로 프로필 표시용 소셜 계정 정보를 조회한다.
   *
   * @param userId 사용자 ID
   * @return 소셜 계정 조회 결과
   * @throws BusinessException 연결된 소셜 계정이 없는 경우
   */
  @Transactional(readOnly = true)
  public SocialIdentityDetails getProfileDetailsByUserId(String userId) {
    SocialIdentity socialIdentity =
        socialIdentityRepository
            .findByUserId(userId)
            .orElseThrow(
                () -> {
                  LOG.warn("프로필 조회 중 소셜 계정 연결 정보가 존재하지 않음");
                  return new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND);
                });

    return new SocialIdentityDetails(socialIdentity.getEmail(), socialIdentity.getProvider());
  }
}
