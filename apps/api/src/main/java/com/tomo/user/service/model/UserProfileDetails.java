package com.tomo.user.service.model;

/**
 * 사용자 프로필 정보.
 *
 * @param nickname 닉네임
 * @param profileImageUrl 프로필 이미지 URL
 */
public record UserProfileDetails(String nickname, String profileImageUrl) {}
