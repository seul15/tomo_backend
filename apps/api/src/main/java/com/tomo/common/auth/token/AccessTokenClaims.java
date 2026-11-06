package com.tomo.common.auth.token;

/**
 * 서명 검증을 통과한 Access Token의 사용자 및 로그인 세션 식별값.
 *
 * @param userId 인증된 사용자 ID
 * @param loginSessionId 로그인 세션 ID
 */
public record AccessTokenClaims(String userId, long loginSessionId) {}
