package com.tomo.user.service.model;

import com.tomo.user.entity.SocialProvider;

/**
 * 소셜 계정의 프로필 표시 정보.
 *
 * @param email 소셜 계정 이메일
 * @param provider 소셜 인증 제공자
 */
public record SocialIdentityDetails(String email, SocialProvider provider) {}
