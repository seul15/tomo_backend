package com.tomo.user.service.model;

import com.tomo.user.entity.SocialProvider;

/**
 * 내 계정 및 프로필 조회 결과.
 *
 * @param nickname 닉네임
 * @param profileImageUrl 프로필 이미지 URL
 * @param email 소셜 계정 이메일
 * @param provider 소셜 인증 제공자
 */
public record UserProfileResult(
    String nickname, String profileImageUrl, String email, SocialProvider provider) {}
