package com.tomo.user.repository;

import com.tomo.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

/** 사용자 영속성 접근 계약. */
public interface UserRepository extends JpaRepository<User, String> {}
