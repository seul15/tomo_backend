package com.tomo.category.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.tomo.category.service.command.CreateCategoryCommand;
import com.tomo.category.service.command.UpdateCategoryCommand;
import com.tomo.category.service.model.CategoryItemResult;
import com.tomo.category.service.model.CategoryListResult;
import com.tomo.common.auth.exception.AuthErrorCode;
import com.tomo.common.exception.BusinessException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CategoryFacadeTest {
  private static final String USER_ID = "user-id";
  private static final Long CATEGORY_ID = 1L;

  @Mock private CategoryCommandService categoryCommandService;
  @Mock private CategoryQueryService categoryQueryService;

  private CategoryFacade facade;

  @BeforeEach
  void setUp() {
    facade = new CategoryFacade(categoryCommandService, categoryQueryService);
  }

  @Test
  @DisplayName("유효한 사용자 ID와 카테고리 생성 요청이면 카테고리 생성 서비스를 호출한다")
  void createsCategory() {
    // given
    CreateCategoryCommand command = new CreateCategoryCommand(USER_ID, "전공");

    // when
    facade.createCategory(command);

    // then
    verify(categoryCommandService).createCategory(USER_ID, "전공");
  }

  @Test
  @DisplayName("사용자 ID가 null이면 INVALID_ACCESS_TOKEN으로 거부한다")
  void rejectsNullUserId() {
    // given
    CreateCategoryCommand command = new CreateCategoryCommand(null, "전공");

    // when
    Throwable thrown = catchThrowable(() -> facade.createCategory(command));

    // then
    assertThat(thrown)
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_ACCESS_TOKEN));

    verifyNoInteractions(categoryCommandService);
  }

  @Test
  @DisplayName("사용자 ID가 공백이면 INVALID_ACCESS_TOKEN으로 거부한다")
  void rejectsBlankUserId() {
    // given
    CreateCategoryCommand command = new CreateCategoryCommand("   ", "전공");

    // when
    Throwable thrown = catchThrowable(() -> facade.createCategory(command));

    // then
    assertThat(thrown)
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_ACCESS_TOKEN));

    verifyNoInteractions(categoryCommandService);
  }

  @Test
  @DisplayName("인증된 사용자는 본인의 공부 카테고리 목록을 조회한다")
  void returnsCategories() {
    // given
    CategoryListResult expected = new CategoryListResult(List.of(new CategoryItemResult(1L, "전공")));
    when(categoryQueryService.getCategories(USER_ID)).thenReturn(expected);

    // when
    CategoryListResult result = facade.getCategories(USER_ID);

    // then
    assertThat(result).isEqualTo(expected);
    verify(categoryQueryService).getCategories(USER_ID);
  }

  @Test
  @DisplayName("카테고리 목록 조회 시 사용자 ID가 공백이면 INVALID_ACCESS_TOKEN으로 거부한다")
  void rejectsGetCategoriesForBlankUserId() {
    // when
    Throwable thrown = catchThrowable(() -> facade.getCategories("   "));

    // then
    assertThat(thrown)
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_ACCESS_TOKEN));

    verifyNoInteractions(categoryQueryService);
  }

  @Test
  @DisplayName("유효한 사용자 ID와 수정 요청이면 카테고리 수정 서비스를 호출한다")
  void updatesCategory() {
    // given
    UpdateCategoryCommand command = new UpdateCategoryCommand(USER_ID, CATEGORY_ID, "교양");

    // when
    facade.updateCategory(command);

    // then
    verify(categoryCommandService).updateCategory(USER_ID, CATEGORY_ID, "교양");
  }

  @Test
  @DisplayName("카테고리 수정 시 사용자 ID가 공백이면 INVALID_ACCESS_TOKEN으로 거부한다")
  void rejectsUpdateCategoryForBlankUserId() {
    // given
    UpdateCategoryCommand command = new UpdateCategoryCommand("   ", CATEGORY_ID, "교양");

    // when
    Throwable thrown = catchThrowable(() -> facade.updateCategory(command));

    // then
    assertThat(thrown)
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_ACCESS_TOKEN));

    verifyNoInteractions(categoryCommandService);
  }

  @Test
  @DisplayName("유효한 사용자 ID와 카테고리 ID이면 카테고리 삭제 서비스를 호출한다")
  void deletesCategory() {
    // when
    facade.deleteCategory(USER_ID, CATEGORY_ID);

    // then
    verify(categoryCommandService).deleteCategory(USER_ID, CATEGORY_ID);
  }

  @Test
  @DisplayName("카테고리 삭제 시 사용자 ID가 공백이면 INVALID_ACCESS_TOKEN으로 거부한다")
  void rejectsDeleteCategoryForBlankUserId() {
    // when
    Throwable thrown = catchThrowable(() -> facade.deleteCategory("   ", CATEGORY_ID));

    // then
    assertThat(thrown)
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_ACCESS_TOKEN));

    verifyNoInteractions(categoryCommandService);
  }
}
