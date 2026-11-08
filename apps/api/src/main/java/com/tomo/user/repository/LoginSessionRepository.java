package com.tomo.user.repository;

import com.tomo.user.entity.LoginSession;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
