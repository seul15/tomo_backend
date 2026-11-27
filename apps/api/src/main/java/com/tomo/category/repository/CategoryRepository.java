package com.tomo.category.repository;

import com.tomo.category.entity.Category;
import com.tomo.category.service.model.CategoryItemResult;
import com.tomo.category.service.model.CategorySnapshot;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 공부 카테고리 영속성 접근 계약. */
public interface CategoryRepository extends JpaRepository<Category, Long> {

  /**
   * 동일한 사용자에게 같은 이름의 카테고리가 이미 존재하는지 확인한다.
   *
   * @param userId 사용자 ID
   * @param categoryName 카테고리 이름
   * @return 동일한 이름의 카테고리가 존재하면 true
   */
  boolean existsByUserIdAndCategoryName(String userId, String categoryName);

  /**
   * 사용자의 공부 카테고리 목록을 ID 오름차순으로 조회한다.
   *
   * @param userId 사용자 ID
   * @return 사용자의 공부 카테고리 목록
   */
  @Query(
      "select new com.tomo.category.service.model.CategoryItemResult(c.id, c.categoryName) "
          + "from Category c where c.userId = :userId order by c.id asc")
  List<CategoryItemResult> findAllItemsByUserIdOrderByIdAsc(@Param("userId") String userId);

  /**
   * 카테고리 ID로 소유자, 이름, 마지막 수정 시각 확인용 스냅샷을 조회한다.
   *
   * @param categoryId 카테고리 ID
   * @return 조회된 카테고리 스냅샷. 없으면 빈 Optional
   */
  @Query(
      "select new com.tomo.category.service.model.CategorySnapshot("
          + "c.id, c.userId, c.categoryName, c.updatedAt) "
          + "from Category c where c.id = :categoryId")
  Optional<CategorySnapshot> findSnapshotById(@Param("categoryId") Long categoryId);

  /**
   * 소유자, 직전 이름, 직전 수정 시각이 모두 일치하는 카테고리의 이름과 수정 시각을 갱신한다.
   *
   * @param categoryId 카테고리 ID
   * @param userId 카테고리 소유 사용자 ID
   * @param previousCategoryName 수정 전 카테고리 이름
   * @param previousUpdatedAt 수정 전 카테고리 수정 시각(UTC)
   * @param categoryName 변경할 카테고리 이름
   * @param updatedAt 새 수정 시각(UTC)
   * @return 갱신된 DB 행 수
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      "update Category c set c.categoryName = :categoryName, c.updatedAt = :updatedAt "
          + "where c.id = :categoryId and c.userId = :userId "
          + "and c.categoryName = :previousCategoryName "
          + "and c.updatedAt = :previousUpdatedAt")
  int updateCategoryName(
      @Param("categoryId") Long categoryId,
      @Param("userId") String userId,
      @Param("previousCategoryName") String previousCategoryName,
      @Param("previousUpdatedAt") LocalDateTime previousUpdatedAt,
      @Param("categoryName") String categoryName,
      @Param("updatedAt") LocalDateTime updatedAt);

  /**
   * 카테고리 ID와 소유자 ID가 모두 일치하는 카테고리를 조건부 일괄 삭제한다.
   *
   * @param categoryId 카테고리 ID
   * @param userId 카테고리 소유 사용자 ID
   * @return 삭제된 DB 행 수
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("delete from Category c where c.id = :categoryId and c.userId = :userId")
  int deleteByIdAndUserId(@Param("categoryId") Long categoryId, @Param("userId") String userId);
}
