package com.tomo.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.LocalDate;

/** document_versions 테이블에 대응하는 약관 문서 버전. */
@Entity
@Table(name = "document_versions")
public class DocumentVersion {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "document_versions_id")
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(name = "document_type", nullable = false)
  private AgreementType documentType;

  @Column(name = "version", nullable = false, length = 30)
  private String version;

  @Lob
  @Column(name = "body", nullable = false, columnDefinition = "LONGTEXT")
  private String body;

  @Column(name = "effective_date", nullable = false)
  private LocalDate effectiveDate;

  @Enumerated(EnumType.STRING)
  @Column(name = "publication_status", nullable = false)
  private DocumentPublicationStatus publicationStatus;

  protected DocumentVersion() {}

  private DocumentVersion(
      AgreementType documentType,
      String version,
      String body,
      LocalDate effectiveDate,
      DocumentPublicationStatus publicationStatus) {
    this.documentType = documentType;
    this.version = version;
    this.body = body;
    this.effectiveDate = effectiveDate;
    this.publicationStatus = publicationStatus;
  }

  /**
   * 명시된 버전과 게시 상태의 문서를 생성한다.
   *
   * @param type 문서 종류
   * @param version 문서 버전
   * @param body 본문
   * @param effectiveDate 시행일
   * @param status 게시 상태
   * @return 문서 버전 엔티티
   */
  public static DocumentVersion create(
      AgreementType type,
      String version,
      String body,
      LocalDate effectiveDate,
      DocumentPublicationStatus status) {

    java.util.Objects.requireNonNull(type);
    java.util.Objects.requireNonNull(version);
    java.util.Objects.requireNonNull(body);
    java.util.Objects.requireNonNull(effectiveDate);
    java.util.Objects.requireNonNull(status);

    return new DocumentVersion(type, version, body, effectiveDate, status);
  }

  public Long getId() {
    return id;
  }

  public AgreementType getDocumentType() {
    return documentType;
  }

  public String getVersion() {
    return version;
  }

  public LocalDate getEffectiveDate() {
    return effectiveDate;
  }

  public DocumentPublicationStatus getPublicationStatus() {
    return publicationStatus;
  }

  public String getBody() {
    return body;
  }
}
