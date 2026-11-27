package com.tomo.category.service;

import com.tomo.category.repository.CategoryRepository;
import com.tomo.category.service.model.CategoryItemResult;
import com.tomo.category.service.model.CategoryListResult;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 공부 카테고리를 조회한다. */
@Service
public class CategoryQueryService {
  private final CategoryRepository categoryRepository;

  /**
   * 카테고리 저장소를 주입받는다.
   *
   * @param categoryRepository 카테고리 저장소
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Spring이 관리하는 JPA Repository를 공유 의존성으로 주입받는다.")
  public CategoryQueryService(CategoryRepository categoryRepository) {
    this.categoryRepository = categoryRepository;
  }

  /**
   * 사용자의 공부 카테고리 목록을 조회한다.
   *
   * @param userId 인증된 사용자 ID
   * @return 사용자의 공부 카테고리 목록 조회 결과
   */
  @Transactional(readOnly = true)
  public CategoryListResult getCategories(String userId) {
    List<CategoryItemResult> categories =
        categoryRepository.findAllItemsByUserIdOrderByIdAsc(userId);
    return new CategoryListResult(categories);
  }
}
