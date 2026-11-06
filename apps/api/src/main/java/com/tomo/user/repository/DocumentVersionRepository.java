package com.tomo.user.repository;

import com.tomo.user.entity.DocumentVersion;
import org.springframework.data.jpa.repository.JpaRepository;

/** 문서 버전 조회·저장 Repository. */
public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, Long> {}
