package com.tomo.user.repository;

import com.tomo.user.entity.User;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 사용자 영속성 접근 계약. */
public interface UserRepository extends JpaRepository<User, String> {
  /**
   * 로그인 중 계정 상태를 일관되게 확인하기 위해 회원 행을 잠근다.
   *
   * @param userId 조회할 사용자 ID
   * @return 잠금 조회된 회원
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select u from User u where u.id = :userId")
  Optional<User> findByIdForUpdate(@Param("userId") String userId);
}
