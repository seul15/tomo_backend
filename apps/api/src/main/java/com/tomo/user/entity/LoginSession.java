package com.tomo.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** login_sessions 테이블에 대응하는 기기별 로그인 세션 엔티티. */
@Entity
@Table(name = "login_sessions")
public class LoginSession {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "login_session_id")
  private Long id;

  @Column(name = "users_id", nullable = false, length = 36, updatable = false)
  private String userId;

  @Column(name = "issued_at", nullable = false, updatable = false)
  private LocalDateTime issuedAt;

  @Column(name = "expires_at", nullable = false)
  private LocalDateTime expiresAt;

  protected LoginSession() {}

  private LoginSession(String userId, LocalDateTime issuedAt, LocalDateTime expiresAt) {
    this.userId = userId;
    this.issuedAt = issuedAt;
    this.expiresAt = expiresAt;
  }

  /**
   * 현재 기기에 대한 로그인 세션을 생성한다.
   *
   * @param userId 세션 소유 사용자 ID
   * @param issuedAt 발급 시각(UTC)
   * @param expiresAt 만료 시각(UTC)
   * @return 로그인 세션
   * @throws IllegalArgumentException 만료 시각이 발급 시각보다 이전인 경우
   */
  public static LoginSession create(
      String userId, LocalDateTime issuedAt, LocalDateTime expiresAt) {

    if (expiresAt.isBefore(issuedAt)) {
      throw new IllegalArgumentException("세션 만료 시각은 발급 시각보다 이전일 수 없다.");
    }

    return new LoginSession(userId, issuedAt, expiresAt);
  }

  public Long getId() {
    return id;
  }

  public String getUserId() {
    return userId;
  }

  public LocalDateTime getIssuedAt() {
    return issuedAt;
  }

  public LocalDateTime getExpiresAt() {
    return expiresAt;
  }
}
