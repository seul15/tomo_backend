package com.tomo.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Kakao 인가 코드 요청.
 *
 * @param authorizationCode Kakao 인가 코드
 */
public record KakaoLoginRequest(
    @Schema(description = "Kakao Authorization Code", requiredMode = Schema.RequiredMode.REQUIRED)
        String authorizationCode) {}
