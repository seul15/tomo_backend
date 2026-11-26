package com.tomo.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Google 인가 코드 및 PKCE 검증값 요청.
 *
 * @param authorizationCode Google 인가 코드
 * @param codeVerifier Google PKCE code verifier
 */
public record GoogleLoginRequest(
    @Schema(description = "Google Authorization Code", requiredMode = Schema.RequiredMode.REQUIRED)
        String authorizationCode,
    @Schema(
            description = "Google PKCE(S256) 원본 검증값, 43~128자",
            minLength = 43,
            maxLength = 128,
            example = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String codeVerifier) {}
