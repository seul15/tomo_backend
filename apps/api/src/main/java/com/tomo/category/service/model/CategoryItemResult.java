package com.tomo.category.service.model;

/**
 * 공부 카테고리 단건 조회 결과.
 *
 * @param categoryId 카테고리 ID
 * @param categoryName 카테고리 이름
 */
public record CategoryItemResult(Long categoryId, String categoryName) {}
