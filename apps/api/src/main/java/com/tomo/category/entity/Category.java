package com.tomo.category.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/** category 테이블에 대응하는 공부 카테고리 엔티티. */
@Entity
@Table(
    name = "category",
    uniqueConstraints = @UniqueConstraint(columnNames = {"users_id", "category_nm"}))
public class Category {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "category_id")
  private Long id;

  @Column(name = "users_id", nullable = false, length = 36, updatable = false)
  private String userId;

  @Column(name = "category_nm", nullable = false, length = 100)
  private String categoryName;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  protected Category() {}

  /**
   * 신규 공부 카테고리를 생성한다.
   *
   * @param userId 카테고리 소유 사용자 ID
   * @param categoryName 카테고리 이름
   */
  public Category(String userId, String categoryName) {
    LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    this.userId = userId;
    this.categoryName = categoryName;
    this.createdAt = now;
    this.updatedAt = now;
  }

  @PreUpdate
  private void updateTimestamp() {
    updatedAt = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
  }

  public Long getId() {
    return id;
  }

  public String getUserId() {
    return userId;
  }

  public String getCategoryName() {
    return categoryName;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }
}
