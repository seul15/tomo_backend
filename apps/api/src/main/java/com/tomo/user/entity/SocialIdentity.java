package com.tomo.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** social_identities 테이블에 대응하는 소셜 인증 정보 엔티티. */
@Entity
@Table(name = "social_identities")
public class SocialIdentity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "social_identities_id")
  private Long id;

  @Column(name = "users_id", nullable = false, length = 36)
  private String userId;

  @Enumerated(EnumType.STRING)
  @Column(name = "provider", nullable = false)
  private SocialProvider provider;

  @Column(name = "provider_subject", nullable = false, length = 255)
  private String providerSubject;

  @Column(name = "email", length = 320)
  private String email;

  @Column(name = "linked_at", nullable = false, updatable = false)
  private LocalDateTime linkedAt;

  protected SocialIdentity() {}

  private SocialIdentity(
      String userId,
      SocialProvider provider,
      String providerSubject,
      String email,
      LocalDateTime linkedAt) {
    this.userId = userId;
    this.provider = provider;
    this.providerSubject = providerSubject;
    this.email = email;
    this.linkedAt = linkedAt;
  }

  /**
   * 사용자 계정에 소셜 인증 정보를 연결한다.
   *
   * @param userId 사용자 ID
   * @param provider 소셜 인증 제공자
   * @param providerSubject 제공자 사용자 식별값
   * @param email 제공자 이메일(선택)
   * @return 생성할 소셜 인증 정보
   */
  public static SocialIdentity link(
      String userId, SocialProvider provider, String providerSubject, String email) {
    return new SocialIdentity(
        userId, provider, providerSubject, email, LocalDateTime.now(ZoneOffset.UTC));
  }

  public Long getId() {
    return id;
  }

  public String getUserId() {
    return userId;
  }

  public SocialProvider getProvider() {
    return provider;
  }

  public String getProviderSubject() {
    return providerSubject;
  }

  public String getEmail() {
    return email;
  }

  public LocalDateTime getLinkedAt() {
    return linkedAt;
  }
}
