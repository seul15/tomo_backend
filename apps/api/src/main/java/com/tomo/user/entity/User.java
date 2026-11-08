package com.tomo.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** users 테이블에 대응하는 사용자 엔티티. */
@Entity
@Table(name = "users")
public class User {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "users_id", nullable = false, length = 36, updatable = false)
  private String id;

  @Column(name = "nickname", length = 50, unique = true)
  private String nickname;

  @Column(name = "profile_image", length = 1000)
  private String profileImage;

  @Enumerated(EnumType.STRING)
  @Column(name = "account_status", nullable = false)
  private AccountStatus accountStatus;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  protected User() {}

  private User(LocalDateTime createdAt) {
    this.accountStatus = AccountStatus.PENDING;
    this.createdAt = createdAt;
    this.updatedAt = createdAt;
  }

  /**
   * 신규 소셜 인증 사용자의 가입 대기 계정을 만든다.
   *
   * @return 가입 대기 사용자
   */
  public static User createPending() {
    return new User(LocalDateTime.now(ZoneOffset.UTC));
  }

  /**
   * 가입 대기 사용자의 가입을 완료한다.
   *
   * @param nickname 가입 닉네임
   * @throws IllegalArgumentException 닉네임이 올바르지 않은 경우
   * @throws IllegalStateException 가입 대기 상태가 아닌 경우
   */
  public void completeRegistration(String nickname) {
    if (accountStatus != AccountStatus.PENDING) {
      throw new IllegalStateException("가입 대기 상태에서만 가입을 완료할 수 있습니다.");
    }

    if (nickname == null || nickname.isBlank()) {
      throw new IllegalArgumentException("닉네임은 필수입니다.");
    }

    String normalizedNickname = nickname.trim();

    if (normalizedNickname.length() > 50) {
      throw new IllegalArgumentException("닉네임은 50자 이하여야 합니다.");
    }

    this.nickname = normalizedNickname;
    this.accountStatus = AccountStatus.ACTIVE;
  }

  @PreUpdate
  private void updateTimestamp() {
    updatedAt = LocalDateTime.now(ZoneOffset.UTC);
  }

  public String getId() {
    return id;
  }

  public String getNickname() {
    return nickname;
  }

  public String getProfileImage() {
    return profileImage;
  }

  public AccountStatus getAccountStatus() {
    return accountStatus;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }
}
