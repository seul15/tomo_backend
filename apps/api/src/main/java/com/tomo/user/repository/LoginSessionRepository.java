package com.tomo.user.repository;

import com.tomo.user.entity.LoginSession;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 기기별 로그인 세션 영속성 접근 계약. */
public interface LoginSessionRepository extends JpaRepository<LoginSession, Long> {
  /**
   * 사용자에게 발급된 로그인 세션 목록을 조회한다.
   *
   * @param userId 사용자 ID
   * @return 로그인 세션 목록
   */
  List<LoginSession> findByUserId(String userId);

  /**
   * 로그인 세션 ID와 소유자 ID가 모두 일치하는 세션을 삭제한다.
   *
   * @param id 로그인 세션 ID
   * @param userId 세션 소유자 ID
   * @return 삭제한 세션 수
   */
  long deleteByIdAndUserId(Long id, String userId);

  /**
   * 로그인 세션을 배타적 DB 잠금으로 조회한다. 호출자는 쓰기 트랜잭션을 열어야 한다.
   *
   * @param sessionId 로그인 세션 ID
   * @return 조회된 세션. 없으면 빈 Optional
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from LoginSession s where s.id = :sessionId")
  Optional<LoginSession> findByIdForUpdate(@Param("sessionId") Long sessionId);

  /**
   * 로그인 세션의 만료 시각을 JPQL UPDATE로 갱신한다. Entity 변경 메서드를 사용하지 않는다.
   *
   * @param sessionId 로그인 세션 ID
   * @param expiresAt 새 만료 시각(UTC)
   * @return 갱신된 DB 행 수
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("update LoginSession s set s.expiresAt = :expiresAt where s.id = :sessionId")
  int updateExpiresAt(
      @Param("sessionId") Long sessionId, @Param("expiresAt") LocalDateTime expiresAt);
}
