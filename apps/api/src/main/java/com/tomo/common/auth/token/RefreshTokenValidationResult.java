package com.tomo.common.auth.token;

/**
 * 검증된 Refresh Token의 사용자 및 로그인 세션 식별값.
 *
 * @param userId 사용자 ID
 * @param sessionId 로그인 세션 ID
 */
public record RefreshTokenValidationResult(String userId, long sessionId) {}
