package com.tomo.category.service.command;

/**
 * 카테고리 생성 업무 실행에 필요한 입력 값.
 *
 * @param userId 인증된 사용자 ID
 * @param categoryName 생성할 공부 카테고리 이름
 */
public record CreateCategoryCommand(String userId, String categoryName) {}
