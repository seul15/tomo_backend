package com.tomo.user.repository;

import com.tomo.user.entity.SocialIdentity;
import com.tomo.user.entity.SocialProvider;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 소셜 계정 영속성 접근 계약. */
public interface SocialIdentityRepository extends JpaRepository<SocialIdentity, Long> {
  /**
   * 제공자와 제공자 사용자 식별값으로 연결 정보를 조회한다.
   *
   * @param provider 소셜 제공자
   * @param providerSubject 제공자 사용자 식별값
   * @return 일치하는 연결 정보
   */
  Optional<SocialIdentity> findByProviderAndProviderSubject(
      SocialProvider provider, String providerSubject);

  /**
   * 소셜 연결을 변경하기 전에 최신 연결 정보를 쓰기 잠금으로 조회한다.
   *
   * @param provider 소셜 제공자
   * @param subject 제공자 사용자 식별값
   * @return 잠금 조회된 소셜 연결
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select s from SocialIdentity s "
          + "where s.provider = :provider "
          + "and s.providerSubject = :subject")
  Optional<SocialIdentity> findByProviderAndProviderSubjectForUpdate(
      @Param("provider") SocialProvider provider, @Param("subject") String subject);

  /**
   * 사용자 ID로 소셜 계정 연결 정보를 조회한다.
   *
   * @param userId 사용자 ID
   * @return 사용자의 소셜 계정 연결 정보
   */
  Optional<SocialIdentity> findByUserId(String userId);
}
