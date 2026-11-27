package com.tomo.category.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.tomo.category.entity.Category;
import com.tomo.category.exception.CategoryErrorCode;
import com.tomo.category.repository.CategoryRepository;
import com.tomo.category.service.model.CategorySnapshot;
import com.tomo.common.exception.BusinessException;
import com.tomo.common.exception.CommonErrorCode;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class CategoryCommandServiceTest {
  private static final String USER_ID = "user-id";
  private static final Long CATEGORY_ID = 1L;
  private static final LocalDateTime SNAPSHOT_UPDATED_AT = LocalDateTime.of(2026, 11, 26, 0, 0, 0);

  @Mock private CategoryRepository categoryRepository;

  private CategoryCommandService service;

  @BeforeEach
  void setUp() {
    service = new CategoryCommandService(categoryRepository);
  }

  @Test
  @DisplayName("유효하고 중복되지 않은 카테고리 이름이면 저장소에 저장한다")
  void createsCategory() {
    // given
    when(categoryRepository.existsByUserIdAndCategoryName(USER_ID, "전공")).thenReturn(false);

    // when
    service.createCategory(USER_ID, "전공");

    // then
    ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
    verify(categoryRepository).saveAndFlush(captor.capture());
    Category saved = captor.getValue();
    assertThat(saved.getUserId()).isEqualTo(USER_ID);
    assertThat(saved.getCategoryName()).isEqualTo("전공");
    assertThat(saved.getCreatedAt()).isNotNull();
    assertThat(saved.getUpdatedAt()).isEqualTo(saved.getCreatedAt());
  }

  @Test
  @DisplayName("유니코드 보조 문자 기준으로 100자 이하인 카테고리 이름이면 저장소에 저장한다")
  void createsCategoryWithSupplementaryCharactersWithinMaxCodePointLength() {
    // given
    String emojiName = "👑".repeat(100);
    when(categoryRepository.existsByUserIdAndCategoryName(USER_ID, emojiName)).thenReturn(false);

    // when
    service.createCategory(USER_ID, emojiName);

    // then
    ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
    verify(categoryRepository).saveAndFlush(captor.capture());
    Category saved = captor.getValue();
    assertThat(saved.getCategoryName()).isEqualTo(emojiName);
  }

  @Test
  @DisplayName("카테고리 이름이 null이면 INVALID_CATEGORY_NAME을 던진다")
  void rejectsNullCategoryName() {
    // when
    Throwable thrown = catchThrowable(() -> service.createCategory(USER_ID, null));

    // then
    assertThat(thrown)
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(CategoryErrorCode.INVALID_CATEGORY_NAME));

    verifyNoInteractions(categoryRepository);
  }

  @Test
  @DisplayName("카테고리 이름이 공백이면 INVALID_CATEGORY_NAME을 던진다")
  void rejectsBlankCategoryName() {
    // when
    Throwable thrown = catchThrowable(() -> service.createCategory(USER_ID, "   "));

    // then
    assertThat(thrown)
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(CategoryErrorCode.INVALID_CATEGORY_NAME));

    verifyNoInteractions(categoryRepository);
  }

  @Test
  @DisplayName("카테고리 이름이 줄 바꿈 없는 공백으로만 이루어져 있으면 INVALID_CATEGORY_NAME을 던진다")
  void rejectsNonBreakingSpaceOnlyCategoryName() {
    // when
    Throwable thrown = catchThrowable(() -> service.createCategory(USER_ID, "\u00A0\u00A0"));

    // then
    assertThat(thrown)
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(CategoryErrorCode.INVALID_CATEGORY_NAME));

    verifyNoInteractions(categoryRepository);
  }

  @Test
  @DisplayName("카테고리 이름 양 끝에 줄 바꿈 없는 공백이 있으면 INVALID_CATEGORY_NAME을 던진다")
  void rejectsCategoryNameWithSurroundingNonBreakingSpace() {
    // when
    Throwable thrown = catchThrowable(() -> service.createCategory(USER_ID, "\u00A0전공\u00A0"));

    // then
    assertThat(thrown)
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(CategoryErrorCode.INVALID_CATEGORY_NAME));

    verifyNoInteractions(categoryRepository);
  }

  @Test
  @DisplayName("카테고리 이름에 앞뒤 공백이 있으면 INVALID_CATEGORY_NAME을 던진다")
  void rejectsCategoryNameWithSurroundingWhitespace() {
    // when
    Throwable thrown = catchThrowable(() -> service.createCategory(USER_ID, " 전공 "));

    // then
    assertThat(thrown)
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(CategoryErrorCode.INVALID_CATEGORY_NAME));

    verifyNoInteractions(categoryRepository);
  }

  @Test
  @DisplayName("카테고리 이름이 100자를 초과하면 INVALID_CATEGORY_NAME을 던진다")
  void rejectsCategoryNameExceedingMaxLength() {
    // given
    String tooLongName = "가".repeat(101);

    // when
    Throwable thrown = catchThrowable(() -> service.createCategory(USER_ID, tooLongName));

    // then
    assertThat(thrown)
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(CategoryErrorCode.INVALID_CATEGORY_NAME));

    verifyNoInteractions(categoryRepository);
  }

  @Test
  @DisplayName("동일한 사용자에게 같은 이름의 카테고리가 존재하면 CATEGORY_ALREADY_EXISTS를 던진다")
  void rejectsDuplicateCategoryName() {
    // given
    when(categoryRepository.existsByUserIdAndCategoryName(USER_ID, "전공")).thenReturn(true);

    // when
    Throwable thrown = catchThrowable(() -> service.createCategory(USER_ID, "전공"));

    // then
    assertThat(thrown)
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(CategoryErrorCode.CATEGORY_ALREADY_EXISTS));
  }

  @Test
  @DisplayName("동시 요청으로 DB 유니크 제약 충돌이 발생하면 CONCURRENT_REQUEST_CONFLICT를 던진다")
  void translatesUniqueConstraintViolationToConcurrentConflict() {
    // given
    when(categoryRepository.existsByUserIdAndCategoryName(USER_ID, "전공")).thenReturn(false);
    when(categoryRepository.saveAndFlush(any(Category.class)))
        .thenThrow(new DataIntegrityViolationException("Duplicate entry"));

    // when
    Throwable thrown = catchThrowable(() -> service.createCategory(USER_ID, "전공"));

    // then
    assertThat(thrown)
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(CategoryErrorCode.CONCURRENT_REQUEST_CONFLICT));
  }

  @Test
  @DisplayName("본인 소유 카테고리를 중복되지 않은 이름으로 수정하면 저장소 UPDATE를 수행한다")
  void updatesCategory() {
    // given
    when(categoryRepository.findSnapshotById(CATEGORY_ID))
        .thenReturn(
            Optional.of(new CategorySnapshot(CATEGORY_ID, USER_ID, "전공", SNAPSHOT_UPDATED_AT)));
    when(categoryRepository.existsByUserIdAndCategoryName(USER_ID, "교양")).thenReturn(false);
    when(categoryRepository.updateCategoryName(
            eq(CATEGORY_ID),
            eq(USER_ID),
            eq("전공"),
            eq(SNAPSHOT_UPDATED_AT),
            eq("교양"),
            any(LocalDateTime.class)))
        .thenReturn(1);

    // when
    service.updateCategory(USER_ID, CATEGORY_ID, "교양");

    // then
    verify(categoryRepository)
        .updateCategoryName(
            eq(CATEGORY_ID),
            eq(USER_ID),
            eq("전공"),
            eq(SNAPSHOT_UPDATED_AT),
            eq("교양"),
            any(LocalDateTime.class));
  }

  @Test
  @DisplayName("카테고리 수정 시 ID가 0 이하이면 INVALID_REQUEST를 던진다")
  void rejectsInvalidCategoryIdWhenUpdating() {
    // when
    Throwable thrown = catchThrowable(() -> service.updateCategory(USER_ID, 0L, "교양"));

    // then
    assertThat(thrown)
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_REQUEST));

    verifyNoInteractions(categoryRepository);
  }

  @Test
  @DisplayName("카테고리 수정 시 이름이 유효하지 않으면 INVALID_REQUEST를 던진다")
  void rejectsInvalidCategoryNameWhenUpdating() {
    // when
    Throwable thrown = catchThrowable(() -> service.updateCategory(USER_ID, CATEGORY_ID, " 교양 "));

    // then
    assertThat(thrown)
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_REQUEST));

    verifyNoInteractions(categoryRepository);
  }

  @Test
  @DisplayName("수정할 카테고리가 존재하지 않으면 CATEGORY_NOT_FOUND를 던진다")
  void rejectsMissingCategoryWhenUpdating() {
    // given
    when(categoryRepository.findSnapshotById(CATEGORY_ID)).thenReturn(Optional.empty());

    // when
    Throwable thrown = catchThrowable(() -> service.updateCategory(USER_ID, CATEGORY_ID, "교양"));

    // then
    assertThat(thrown)
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(CategoryErrorCode.CATEGORY_NOT_FOUND));
  }

  @Test
  @DisplayName("다른 사용자의 카테고리를 수정하려고 하면 CATEGORY_UPDATE_FORBIDDEN을 던진다")
  void rejectsUpdatingOtherUsersCategory() {
    // given
    when(categoryRepository.findSnapshotById(CATEGORY_ID))
        .thenReturn(
            Optional.of(
                new CategorySnapshot(CATEGORY_ID, "other-user", "전공", SNAPSHOT_UPDATED_AT)));

    // when
    Throwable thrown = catchThrowable(() -> service.updateCategory(USER_ID, CATEGORY_ID, "교양"));

    // then
    assertThat(thrown)
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(CategoryErrorCode.CATEGORY_UPDATE_FORBIDDEN));
  }

  @Test
  @DisplayName("기존과 동일한 이름으로 카테고리를 수정하려고 하면 CATEGORY_ALREADY_EXISTS를 던진다")
  void rejectsUpdatingToSameCategoryName() {
    // given
    when(categoryRepository.findSnapshotById(CATEGORY_ID))
        .thenReturn(
            Optional.of(new CategorySnapshot(CATEGORY_ID, USER_ID, "전공", SNAPSHOT_UPDATED_AT)));

    // when
    Throwable thrown = catchThrowable(() -> service.updateCategory(USER_ID, CATEGORY_ID, "전공"));

    // then
    assertThat(thrown)
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(CategoryErrorCode.CATEGORY_ALREADY_EXISTS));
  }

  @Test
  @DisplayName("이미 존재하는 다른 카테고리 이름으로 수정하려고 하면 CATEGORY_ALREADY_EXISTS를 던진다")
  void rejectsUpdatingToExistingCategoryName() {
    // given
    when(categoryRepository.findSnapshotById(CATEGORY_ID))
        .thenReturn(
            Optional.of(new CategorySnapshot(CATEGORY_ID, USER_ID, "전공", SNAPSHOT_UPDATED_AT)));
    when(categoryRepository.existsByUserIdAndCategoryName(USER_ID, "교양")).thenReturn(true);

    // when
    Throwable thrown = catchThrowable(() -> service.updateCategory(USER_ID, CATEGORY_ID, "교양"));

    // then
    assertThat(thrown)
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(CategoryErrorCode.CATEGORY_ALREADY_EXISTS));
  }

  @Test
  @DisplayName("동시 수정으로 갱신된 행이 0건이면 CATEGORY_UPDATE_CONFLICT를 던진다")
  void rejectsConcurrentUpdateWhenZeroRowsUpdated() {
    // given
    when(categoryRepository.findSnapshotById(CATEGORY_ID))
        .thenReturn(
            Optional.of(new CategorySnapshot(CATEGORY_ID, USER_ID, "전공", SNAPSHOT_UPDATED_AT)));
    when(categoryRepository.existsByUserIdAndCategoryName(USER_ID, "교양")).thenReturn(false);
    when(categoryRepository.updateCategoryName(
            eq(CATEGORY_ID),
            eq(USER_ID),
            eq("전공"),
            eq(SNAPSHOT_UPDATED_AT),
            eq("교양"),
            any(LocalDateTime.class)))
        .thenReturn(0);

    // when
    Throwable thrown = catchThrowable(() -> service.updateCategory(USER_ID, CATEGORY_ID, "교양"));

    // then
    assertThat(thrown)
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(CategoryErrorCode.CATEGORY_UPDATE_CONFLICT));
  }

  @Test
  @DisplayName("동시 수정으로 유니크 제약 충돌이 발생하면 CATEGORY_UPDATE_CONFLICT를 던진다")
  void translatesUniqueViolationOnUpdateToCategoryUpdateConflict() {
    // given
    when(categoryRepository.findSnapshotById(CATEGORY_ID))
        .thenReturn(
            Optional.of(new CategorySnapshot(CATEGORY_ID, USER_ID, "전공", SNAPSHOT_UPDATED_AT)));
    when(categoryRepository.existsByUserIdAndCategoryName(USER_ID, "교양")).thenReturn(false);
    when(categoryRepository.updateCategoryName(
            eq(CATEGORY_ID),
            eq(USER_ID),
            eq("전공"),
            eq(SNAPSHOT_UPDATED_AT),
            eq("교양"),
            any(LocalDateTime.class)))
        .thenThrow(new DataIntegrityViolationException("Duplicate entry"));

    // when
    Throwable thrown = catchThrowable(() -> service.updateCategory(USER_ID, CATEGORY_ID, "교양"));

    // then
    assertThat(thrown)
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(CategoryErrorCode.CATEGORY_UPDATE_CONFLICT));
  }

  @Test
  @DisplayName("본인 소유 카테고리를 삭제하면 저장소 DELETE를 수행한다")
  void deletesCategory() {
    // given
    when(categoryRepository.findSnapshotById(CATEGORY_ID))
        .thenReturn(
            Optional.of(new CategorySnapshot(CATEGORY_ID, USER_ID, "전공", SNAPSHOT_UPDATED_AT)));
    when(categoryRepository.deleteByIdAndUserId(CATEGORY_ID, USER_ID)).thenReturn(1);

    // when
    service.deleteCategory(USER_ID, CATEGORY_ID);

    // then
    verify(categoryRepository).deleteByIdAndUserId(CATEGORY_ID, USER_ID);
  }

  @Test
  @DisplayName("카테고리 삭제 시 ID가 0 이하이면 INVALID_CATEGORY_ID를 던진다")
  void rejectsInvalidCategoryIdWhenDeleting() {
    // when
    Throwable thrown = catchThrowable(() -> service.deleteCategory(USER_ID, 0L));

    // then
    assertThat(thrown)
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(CategoryErrorCode.INVALID_CATEGORY_ID));

    verifyNoInteractions(categoryRepository);
  }

  @Test
  @DisplayName("삭제할 카테고리가 존재하지 않으면 CATEGORY_NOT_FOUND를 던진다")
  void rejectsMissingCategoryWhenDeleting() {
    // given
    when(categoryRepository.findSnapshotById(CATEGORY_ID)).thenReturn(Optional.empty());

    // when
    Throwable thrown = catchThrowable(() -> service.deleteCategory(USER_ID, CATEGORY_ID));

    // then
    assertThat(thrown)
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(CategoryErrorCode.CATEGORY_NOT_FOUND));
  }

  @Test
  @DisplayName("다른 사용자의 카테고리를 삭제하려고 하면 CATEGORY_DELETE_FORBIDDEN을 던진다")
  void rejectsDeletingOtherUsersCategory() {
    // given
    when(categoryRepository.findSnapshotById(CATEGORY_ID))
        .thenReturn(
            Optional.of(
                new CategorySnapshot(CATEGORY_ID, "other-user", "전공", SNAPSHOT_UPDATED_AT)));

    // when
    Throwable thrown = catchThrowable(() -> service.deleteCategory(USER_ID, CATEGORY_ID));

    // then
    assertThat(thrown)
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(CategoryErrorCode.CATEGORY_DELETE_FORBIDDEN));
  }

  @Test
  @DisplayName("동시 삭제로 삭제된 행이 0건이면 CATEGORY_DELETE_CONFLICT를 던진다")
  void rejectsConcurrentDeleteWhenZeroRowsDeleted() {
    // given
    when(categoryRepository.findSnapshotById(CATEGORY_ID))
        .thenReturn(
            Optional.of(new CategorySnapshot(CATEGORY_ID, USER_ID, "전공", SNAPSHOT_UPDATED_AT)));
    when(categoryRepository.deleteByIdAndUserId(CATEGORY_ID, USER_ID)).thenReturn(0);

    // when
    Throwable thrown = catchThrowable(() -> service.deleteCategory(USER_ID, CATEGORY_ID));

    // then
    assertThat(thrown)
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(CategoryErrorCode.CATEGORY_DELETE_CONFLICT));
  }
}
