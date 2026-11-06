package com.tomo.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/** users 테이블에 대응하는 사용자 엔티티. */
@Entity
@Table(name = "users")
public class User {
  @Id
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

  private User(String id, LocalDateTime createdAt) {
    this.id = id;
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
    return new User(UUID.randomUUID().toString(), LocalDateTime.now(ZoneOffset.UTC));
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
