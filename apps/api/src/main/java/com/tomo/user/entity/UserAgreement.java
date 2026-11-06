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

/** user_agreement 테이블에 대응하는 회원 가입 동의 이력. */
@Entity
@Table(name = "user_agreement")
public class UserAgreement {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "user_agreement_id")
  private Long id;

  @Column(name = "users_id", nullable = false, length = 36)
  private String userId;

  @Enumerated(EnumType.STRING)
  @Column(name = "agreement_type", nullable = false)
  private AgreementType agreementType;

  @Column(name = "document_versions_id", nullable = false)
  private Long documentVersionId;

  @Column(name = "agreed_yn", nullable = false, length = 1)
  private String agreedYn;

  @Column(name = "agreed_at", nullable = false)
  private LocalDateTime agreedAt;

  protected UserAgreement() {}

  private UserAgreement(
      String userId,
      AgreementType agreementType,
      Long documentVersionId,
      boolean agreed,
      LocalDateTime agreedAt) {
    this.userId = userId;
    this.agreementType = agreementType;
    this.documentVersionId = documentVersionId;
    this.agreedYn = agreed ? "1" : "0";
    this.agreedAt = agreedAt;
  }

  /**
   * 해당 문서 버전에 대한 동의 여부 이력을 생성한다.
   *
   * @param userId 사용자 ID
   * @param agreementType 동의 항목
   * @param documentVersionId 문서 버전 ID
   * @param agreed 동의 여부
   * @param agreedAt 동의 처리 시각
   * @return 동의 이력 엔티티
   */
  public static UserAgreement record(
      String userId,
      AgreementType agreementType,
      Long documentVersionId,
      boolean agreed,
      LocalDateTime agreedAt) {
    return new UserAgreement(userId, agreementType, documentVersionId, agreed, agreedAt);
  }

  public Long getId() {
    return id;
  }

  public String getUserId() {
    return userId;
  }

  public AgreementType getAgreementType() {
    return agreementType;
  }

  public Long getDocumentVersionId() {
    return documentVersionId;
  }

  public String getAgreedYn() {
    return agreedYn;
  }

  public LocalDateTime getAgreedAt() {
    return agreedAt;
  }
}
