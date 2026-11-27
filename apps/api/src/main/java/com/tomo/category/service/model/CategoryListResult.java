package com.tomo.category.service.model;

import java.util.List;

/**
 * 공부 카테고리 목록 조회 결과.
 *
 * @param categories 공부 카테고리 목록
 */
public record CategoryListResult(List<CategoryItemResult> categories) {

  /**
   * 카테고리 목록을 방어적 복사하여 불변 결과로 생성한다.
   *
   * @param categories 공부 카테고리 목록
   */
  public CategoryListResult {
    categories = List.copyOf(categories);
  }
}
