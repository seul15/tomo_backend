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
}
