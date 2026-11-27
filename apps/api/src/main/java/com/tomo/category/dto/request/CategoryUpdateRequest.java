package com.tomo.category.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tomo.category.service.command.UpdateCategoryCommand;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 카테고리 수정 요청 DTO.
 *
 * @param categoryName 변경할 공부 카테고리 이름
 */
@Schema(description = "카테고리 수정 요청")
public record CategoryUpdateRequest(
    @Schema(
            name = "category_nm",
            description = "공부 카테고리 (앞뒤 공백 불가, 1~100자)",
            example = "전공",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("category_nm")
        String categoryName) {

  /**
   * 요청 DTO를 카테고리 수정 Command로 변환한다.
   *
   * @param userId 인증된 사용자 ID
   * @param categoryId 수정할 카테고리 ID
   * @return 카테고리 수정 Command
   */
  public UpdateCategoryCommand toCommand(String userId, Long categoryId) {
    return new UpdateCategoryCommand(userId, categoryId, categoryName);
  }
}
