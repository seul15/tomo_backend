package com.tomo.category.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tomo.category.service.model.CategoryItemResult;
import com.tomo.category.service.model.CategoryListResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 공부 카테고리 목록 조회 응답.
 *
 * @param category 공부 카테고리 목록
 */
@Schema(description = "공부 카테고리 목록 조회 응답")
public record CategoryListResponse(
    @Schema(description = "카테고리 목록", requiredMode = Schema.RequiredMode.REQUIRED)
        List<CategoryItemResponse> category) {

  /**
   * 카테고리 목록을 방어적 복사하여 불변 응답으로 생성한다.
   *
   * @param category 카테고리 응답 목록
   */
  public CategoryListResponse {
    category = List.copyOf(category);
  }

  /**
   * 서비스 조회 결과를 HTTP 응답으로 변환한다.
   *
   * @param result 공부 카테고리 목록 조회 결과
   * @return 공부 카테고리 목록 조회 응답
   */
  public static CategoryListResponse from(CategoryListResult result) {
    List<CategoryItemResponse> items =
        result.categories().stream().map(CategoryItemResponse::from).toList();
    return new CategoryListResponse(items);
  }

  /**
   * 공부 카테고리 단건 응답 객체.
   *
   * @param categoryId 카테고리 ID
   * @param categoryName 공부 카테고리 이름
   */
  @Schema(description = "카테고리 객체")
  public record CategoryItemResponse(
      @Schema(description = "카테고리 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
          Long categoryId,
      @Schema(
              name = "category_nm",
              description = "카테고리",
              example = "전공",
              requiredMode = Schema.RequiredMode.REQUIRED)
          @JsonProperty("category_nm")
          String categoryName) {

    /**
     * 단건 조회 결과를 응답 객체로 변환한다.
     *
     * @param item 단건 조회 결과
     * @return 단건 응답 객체
     */
    public static CategoryItemResponse from(CategoryItemResult item) {
      return new CategoryItemResponse(item.categoryId(), item.categoryName());
    }
  }
}
