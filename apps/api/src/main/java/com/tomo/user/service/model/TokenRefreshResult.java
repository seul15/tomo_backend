package com.tomo.user.service.model;

/**
 * Refresh Token 재발급 업무의 결과.
 *
 * @param accessToken 새 Access Token
 * @param refreshToken 새 Refresh Token
 * @param accessTokenExpiresIn Access Token 유효기간(초)
 * @param refreshTokenExpiresIn Refresh Token 유효기간(초)
 */
public record TokenRefreshResult(
    String accessToken,
    String refreshToken,
    long accessTokenExpiresIn,
    long refreshTokenExpiresIn) {}
