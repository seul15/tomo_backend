package com.tomo.category.service.command;

/**
 * 카테고리 수정 업무 실행에 필요한 입력 값.
 *
 * @param userId 인증된 사용자 ID
 * @param categoryId 수정할 카테고리 ID
 * @param categoryName 변경할 공부 카테고리 이름
 */
public record UpdateCategoryCommand(String userId, Long categoryId, String categoryName) {}
