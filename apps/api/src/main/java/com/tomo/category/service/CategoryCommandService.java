package com.tomo.category.service;

import com.tomo.category.entity.Category;
import com.tomo.category.exception.CategoryErrorCode;
import com.tomo.category.repository.CategoryRepository;
import com.tomo.category.service.model.CategorySnapshot;
import com.tomo.common.exception.BusinessException;
import com.tomo.common.exception.CommonErrorCode;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 공부 카테고리의 생성·수정·삭제를 수행한다. */
@Service
public class CategoryCommandService {
  private static final Logger LOG = LoggerFactory.getLogger(CategoryCommandService.class);

  /** 카테고리 이름 최대 길이. */
  public static final int MAX_NAME_LENGTH = 100;

  private final CategoryRepository categoryRepository;

  /**
   * 카테고리 저장소를 주입받는다.
   *
   * @param categoryRepository 카테고리 저장소
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Spring이 관리하는 JPA Repository를 공유 의존성으로 주입받는다.")
  public CategoryCommandService(CategoryRepository categoryRepository) {
    this.categoryRepository = categoryRepository;
  }

  /**
   * 사용자 소유의 공부 카테고리를 생성한다.
   *
   * @param userId 인증된 사용자 ID
   * @param categoryName 생성할 카테고리 이름
   * @throws BusinessException 이름 형식이 잘못되었거나 이미 동일한 이름의 카테고리가 존재하거나 동시 요청이 충돌한 경우
   */
  @Transactional
  public void createCategory(String userId, String categoryName) {
    validateCategoryName(categoryName);

    if (categoryRepository.existsByUserIdAndCategoryName(userId, categoryName)) {
      LOG.debug("카테고리 생성 거부. 동일한 이름의 카테고리 존재");
      throw new BusinessException(CategoryErrorCode.CATEGORY_ALREADY_EXISTS);
    }

    Category category = new Category(userId, categoryName);

    try {
      categoryRepository.saveAndFlush(category);
    } catch (DataIntegrityViolationException exception) {
      LOG.debug("카테고리 생성 동시 요청 충돌 발생");
      throw new BusinessException(CategoryErrorCode.CONCURRENT_REQUEST_CONFLICT);
    }
  }

  /**
   * 사용자 소유의 공부 카테고리 이름을 수정한다.
   *
   * @param userId 인증된 사용자 ID
   * @param categoryId 수정할 카테고리 ID
   * @param categoryName 변경할 카테고리 이름
   * @throws BusinessException 입력값이 유효하지 않거나, 카테고리가 존재하지 않거나, 수정 권한이 없거나, 이름이 중복되거나, 동시 수정 충돌이 발생한
   *     경우
   */
  @Transactional
  public void updateCategory(String userId, Long categoryId, String categoryName) {
    validateUpdateRequest(categoryId, categoryName);

    CategorySnapshot snapshot =
        categoryRepository
            .findSnapshotById(categoryId)
            .orElseThrow(
                () -> {
                  LOG.debug("카테고리 수정 거부. 존재하지 않는 카테고리");
                  return new BusinessException(CategoryErrorCode.CATEGORY_NOT_FOUND);
                });

    if (!userId.equals(snapshot.userId())) {
      LOG.debug("카테고리 수정 거부. 소유자가 아님");
      throw new BusinessException(CategoryErrorCode.CATEGORY_UPDATE_FORBIDDEN);
    }

    if (categoryName.equals(snapshot.categoryName())
        || categoryRepository.existsByUserIdAndCategoryName(userId, categoryName)) {
      LOG.debug("카테고리 수정 거부. 동일한 이름의 카테고리 존재");
      throw new BusinessException(CategoryErrorCode.CATEGORY_ALREADY_EXISTS);
    }

    LocalDateTime nextUpdatedAt = nextUpdatedAt(snapshot.updatedAt());
    int updatedCount;
    try {
      updatedCount =
          categoryRepository.updateCategoryName(
              categoryId,
              userId,
              snapshot.categoryName(),
              snapshot.updatedAt(),
              categoryName,
              nextUpdatedAt);
    } catch (DataIntegrityViolationException exception) {
      LOG.debug("카테고리 수정 동시 요청 유니크 제약 충돌 발생");
      throw new BusinessException(CategoryErrorCode.CATEGORY_UPDATE_CONFLICT);
    }

    if (updatedCount != 1) {
      LOG.debug("카테고리 수정 동시 요청 상태 충돌 발생");
      throw new BusinessException(CategoryErrorCode.CATEGORY_UPDATE_CONFLICT);
    }
  }

  /**
   * 사용자 소유의 공부 카테고리를 삭제한다.
   *
   * @param userId 인증된 사용자 ID
   * @param categoryId 삭제할 카테고리 ID
   * @throws BusinessException 카테고리 ID가 유효하지 않거나, 카테고리가 존재하지 않거나, 삭제 권한이 없거나, 동시 삭제 충돌이 발생한 경우
   */
  @Transactional
  public void deleteCategory(String userId, Long categoryId) {
    validateDeleteCategoryId(categoryId);

    CategorySnapshot snapshot =
        categoryRepository
            .findSnapshotById(categoryId)
            .orElseThrow(
                () -> {
                  LOG.debug("카테고리 삭제 거부. 존재하지 않는 카테고리");
                  return new BusinessException(CategoryErrorCode.CATEGORY_NOT_FOUND);
                });

    if (!userId.equals(snapshot.userId())) {
      LOG.debug("카테고리 삭제 거부. 소유자가 아님");
      throw new BusinessException(CategoryErrorCode.CATEGORY_DELETE_FORBIDDEN);
    }

    int deletedCount = categoryRepository.deleteByIdAndUserId(categoryId, userId);
    if (deletedCount != 1) {
      LOG.debug("카테고리 삭제 동시 요청 충돌 발생");
      throw new BusinessException(CategoryErrorCode.CATEGORY_DELETE_CONFLICT);
    }
  }

  /**
   * 카테고리 생성 시 이름이 유효성 검증 조건을 충족하는지 확인한다.
   *
   * <p>null 또는 공백이거나, 앞뒤 공백이 포함되어 있거나, 최대 길이를 초과하면 거부한다.
   *
   * @param categoryName 검증할 카테고리 이름
   * @throws BusinessException 카테고리 이름이 유효하지 않은 경우
   */
  public static void validateCategoryName(String categoryName) {
    if (!isValidCategoryName(categoryName)) {
      LOG.debug("카테고리 이름 유효성 검증 실패");
      throw new BusinessException(CategoryErrorCode.INVALID_CATEGORY_NAME);
    }
  }

  /**
   * 카테고리 수정 요청의 ID와 이름이 유효성 검증 조건을 충족하는지 확인한다.
   *
   * @param categoryId 검증할 카테고리 ID
   * @param categoryName 검증할 카테고리 이름
   * @throws BusinessException 카테고리 ID 또는 이름이 유효하지 않은 경우
   */
  public static void validateUpdateRequest(Long categoryId, String categoryName) {
    if (categoryId == null || categoryId <= 0 || !isValidCategoryName(categoryName)) {
      LOG.debug("카테고리 수정 요청 유효성 검증 실패");
      throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
    }
  }

  /**
   * 카테고리 삭제 요청의 ID가 유효성 검증 조건을 충족하는지 확인한다.
   *
   * @param categoryId 검증할 카테고리 ID
   * @throws BusinessException 카테고리 ID가 유효하지 않은 경우
   */
  public static void validateDeleteCategoryId(Long categoryId) {
    if (categoryId == null || categoryId <= 0) {
      LOG.debug("카테고리 삭제 ID 유효성 검증 실패");
      throw new BusinessException(CategoryErrorCode.INVALID_CATEGORY_ID);
    }
  }

  private static LocalDateTime nextUpdatedAt(LocalDateTime previousUpdatedAt) {
    LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    if (previousUpdatedAt != null && !now.isAfter(previousUpdatedAt)) {
      return previousUpdatedAt.plusNanos(1000L);
    }
    return now;
  }

  private static boolean isValidCategoryName(String categoryName) {
    if (categoryName == null
        || categoryName.isBlank()
        || !categoryName.equals(categoryName.strip())
        || categoryName.codePointCount(0, categoryName.length()) > MAX_NAME_LENGTH) {
      return false;
    }

    int firstCodePoint = categoryName.codePointAt(0);
    int lastCodePoint = categoryName.codePointBefore(categoryName.length());
    return !isWhitespaceOrSpaceSeparator(firstCodePoint)
        && !isWhitespaceOrSpaceSeparator(lastCodePoint)
        && categoryName
            .codePoints()
            .anyMatch(codePoint -> !isWhitespaceOrSpaceSeparator(codePoint));
  }

  private static boolean isWhitespaceOrSpaceSeparator(int codePoint) {
    return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
  }
}
