package com.tomo.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.Objects;

/** user_agreement 테이블에 대응하는 회원 가입 동의 이력. */
@Entity
@Table(
    name = "user_agreement",
    uniqueConstraints = {
      @UniqueConstraint(columnNames = {"users_id", "agreement_type", "document_versions_id"})
    })
public class UserAgreement {
  private static final String AGREED = "1";
  private static final String NOT_AGREED = "0";

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
    this.agreedYn = agreed ? AGREED : NOT_AGREED;
    this.agreedAt = agreedAt;
  }

  /**
   * 문서 버전과 일치하는 동의 유형으로 동의 이력을 생성한다.
   *
   * @param userId 사용자 ID
   * @param documentVersion 저장된 동의 대상 문서 버전
   * @param agreed 동의 여부
   * @param agreedAt 동의 처리 시각
   * @return 동의 이력 엔티티
   * @throws NullPointerException 문서 또는 문서 버전 ID가 없는 경우
   */
  public static UserAgreement record(
      String userId, DocumentVersion documentVersion, boolean agreed, LocalDateTime agreedAt) {
    Objects.requireNonNull(documentVersion);

    Long documentVersionId = Objects.requireNonNull(documentVersion.getId());

    return new UserAgreement(
        userId, documentVersion.getDocumentType(), documentVersionId, agreed, agreedAt);
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
