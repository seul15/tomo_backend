package com.tomo.user.repository;

import com.tomo.user.entity.UserAgreement;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** 사용자 동의 이력 조회·저장 Repository. */
public interface UserAgreementRepository extends JpaRepository<UserAgreement, Long> {
  /**
   * 사용자의 가입 동의 이력을 모두 조회한다.
   *
   * @param userId 사용자 ID
   * @return 동의 이력
   */
  List<UserAgreement> findByUserId(String userId);
}
