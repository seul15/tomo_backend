package com.tomo.category.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.tomo.category.entity.Category;
import com.tomo.category.service.model.CategoryItemResult;
import com.tomo.category.service.model.CategorySnapshot;
import com.tomo.user.entity.User;
import com.tomo.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CategoryPersistenceTest {
  @Autowired private EntityManager entityManager;
  @Autowired private UserRepository userRepository;
  @Autowired private CategoryRepository categoryRepository;

  @Test
  @DisplayName("신규 카테고리는 AUTO_INCREMENT ID를 발급받아 저장되고 사용자별 이름 존재 여부를 조회한다")
  void savesCategoryWithAutoIncrementId() {
    // given
    User user = userRepository.saveAndFlush(User.createPending());

    // when
    Category saved = categoryRepository.saveAndFlush(new Category(user.getId(), "전공"));
    entityManager.clear();

    // then
    assertThat(saved.getId()).isNotNull().isPositive();
    assertThat(categoryRepository.existsByUserIdAndCategoryName(user.getId(), "전공")).isTrue();
    assertThat(categoryRepository.existsByUserIdAndCategoryName(user.getId(), "교양")).isFalse();
  }

  @Test
  @DisplayName("같은 사용자가 동일한 이름의 카테고리를 중복 저장하면 유니크 제약 위반이 발생한다")
  void rejectsDuplicateCategoryForSameUser() {
    // given
    User user = userRepository.saveAndFlush(User.createPending());
    categoryRepository.saveAndFlush(new Category(user.getId(), "전공"));

    // when
    Throwable thrown =
        catchThrowable(() -> categoryRepository.saveAndFlush(new Category(user.getId(), "전공")));

    // then
    assertThat(thrown).isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("서로 다른 사용자는 동일한 이름의 카테고리를 각각 저장할 수 있다")
  void allowsSameCategoryNameForDifferentUsers() {
    // given
    User firstUser = userRepository.saveAndFlush(User.createPending());
    User secondUser = userRepository.saveAndFlush(User.createPending());

    // when
    Category firstSaved = categoryRepository.saveAndFlush(new Category(firstUser.getId(), "전공"));
    Category secondSaved = categoryRepository.saveAndFlush(new Category(secondUser.getId(), "전공"));
    entityManager.clear();

    // then
    assertThat(firstSaved.getId()).isNotEqualTo(secondSaved.getId());
    assertThat(categoryRepository.existsByUserIdAndCategoryName(firstUser.getId(), "전공")).isTrue();
    assertThat(categoryRepository.existsByUserIdAndCategoryName(secondUser.getId(), "전공")).isTrue();
  }

  @Test
  @DisplayName("사용자의 카테고리 목록을 삽입 순서나 이름 순서와 무관하게 ID 오름차순으로 조회한다")
  void findsAllCategoriesByUserIdOrderedByIdAsc() {
    // given
    User owner = userRepository.saveAndFlush(User.createPending());
    User otherUser = userRepository.saveAndFlush(User.createPending());

    LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    entityManager
        .createNativeQuery(
            "INSERT INTO category (category_id, users_id, category_nm, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?)")
        .setParameter(1, 200L)
        .setParameter(2, owner.getId())
        .setParameter(3, "교양")
        .setParameter(4, now)
        .setParameter(5, now)
        .executeUpdate();
    entityManager
        .createNativeQuery(
            "INSERT INTO category (category_id, users_id, category_nm, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?)")
        .setParameter(1, 100L)
        .setParameter(2, owner.getId())
        .setParameter(3, "전공")
        .setParameter(4, now)
        .setParameter(5, now)
        .executeUpdate();
    categoryRepository.saveAndFlush(new Category(otherUser.getId(), "자격증"));
    entityManager.flush();
    entityManager.clear();

    // when
    List<CategoryItemResult> results =
        categoryRepository.findAllItemsByUserIdOrderByIdAsc(owner.getId());

    // then
    assertThat(results)
        .containsExactly(new CategoryItemResult(100L, "전공"), new CategoryItemResult(200L, "교양"));
  }

  @Test
  @DisplayName("카테고리 스냅샷을 조회하고 소유자·직전 이름·직전 수정 시각이 일치할 때만 이름을 갱신한다")
  void findsSnapshotAndUpdatesCategoryName() {
    // given
    User owner = userRepository.saveAndFlush(User.createPending());
    Category saved = categoryRepository.saveAndFlush(new Category(owner.getId(), "전공"));
    Long categoryId = saved.getId();
    LocalDateTime initialUpdatedAt = saved.getUpdatedAt();
    LocalDateTime nextUpdatedAt =
        LocalDateTime.now(ZoneOffset.UTC).plusMinutes(5).truncatedTo(ChronoUnit.MICROS);
    entityManager.clear();

    // when
    Optional<CategorySnapshot> beforeUpdate = categoryRepository.findSnapshotById(categoryId);
    int staleTimestampCount =
        categoryRepository.updateCategoryName(
            categoryId, owner.getId(), "전공", initialUpdatedAt.minusSeconds(1), "교양", nextUpdatedAt);
    int updatedCount =
        categoryRepository.updateCategoryName(
            categoryId, owner.getId(), "전공", initialUpdatedAt, "교양", nextUpdatedAt);
    Optional<CategorySnapshot> afterUpdate = categoryRepository.findSnapshotById(categoryId);

    // then
    assertThat(beforeUpdate)
        .contains(new CategorySnapshot(categoryId, owner.getId(), "전공", initialUpdatedAt));
    assertThat(staleTimestampCount).isZero();
    assertThat(updatedCount).isEqualTo(1);
    assertThat(afterUpdate)
        .contains(new CategorySnapshot(categoryId, owner.getId(), "교양", nextUpdatedAt));
  }

  @Test
  @DisplayName("소유자 ID가 일치하는 경우에만 카테고리를 삭제하고 삭제된 건수를 반환한다")
  void deletesCategoryByIdAndUserId() {
    // given
    User owner = userRepository.saveAndFlush(User.createPending());
    User otherUser = userRepository.saveAndFlush(User.createPending());
    Category saved = categoryRepository.saveAndFlush(new Category(owner.getId(), "전공"));
    Long categoryId = saved.getId();
    entityManager.clear();

    // when
    int mismatchedDeleteCount =
        categoryRepository.deleteByIdAndUserId(categoryId, otherUser.getId());
    int matchedDeleteCount = categoryRepository.deleteByIdAndUserId(categoryId, owner.getId());

    // then
    assertThat(mismatchedDeleteCount).isZero();
    assertThat(matchedDeleteCount).isEqualTo(1);
    assertThat(categoryRepository.findSnapshotById(categoryId)).isEmpty();
  }
}
