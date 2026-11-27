package com.tomo.category.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tomo.category.repository.CategoryRepository;
import com.tomo.category.service.model.CategoryItemResult;
import com.tomo.category.service.model.CategoryListResult;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CategoryQueryServiceTest {
  private static final String USER_ID = "user-id";

  @Mock private CategoryRepository categoryRepository;

  private CategoryQueryService service;

  @BeforeEach
  void setUp() {
    service = new CategoryQueryService(categoryRepository);
  }

  @Test
  @DisplayName("사용자의 카테고리 목록을 ID 오름차순으로 조회해 반환한다")
  void returnsUserCategories() {
    // given
    List<CategoryItemResult> items =
        List.of(new CategoryItemResult(1L, "전공"), new CategoryItemResult(2L, "교양"));
    when(categoryRepository.findAllItemsByUserIdOrderByIdAsc(USER_ID)).thenReturn(items);

    // when
    CategoryListResult result = service.getCategories(USER_ID);

    // then
    assertThat(result.categories()).containsExactlyElementsOf(items);
    verify(categoryRepository).findAllItemsByUserIdOrderByIdAsc(USER_ID);
  }
}
