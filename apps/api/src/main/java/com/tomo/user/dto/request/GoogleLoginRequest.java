package com.tomo.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Google Authorization Code를 서버에 전달하는 로그인 요청.
 *
 * <p>codeVerifier는 프론트엔드에서 PKCE(S256) 인증 요청에 사용한 원본 검증값이며, Google 토큰 교환 요청에서 검증된다.
 *
 * @param authorizationCode Google에서 발급받은 인가 코드
 * @param codeVerifier 43~128자의 PKCE 원본 검증값
 */
@Schema(description = "Google 소셜 로그인 요청")
public record GoogleLoginRequest(
    @Schema(
            description = "Google 인증 완료 후 발급된 일회용 Authorization Code",
            example = "google-authorization-code",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String authorizationCode,
    @Schema(
            description = "Google PKCE(S256) 검증용 codeVerifier. 43~128자의 허용 문자로 구성",
            example = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            minLength = 43,
            maxLength = 128,
            requiredMode = Schema.RequiredMode.REQUIRED)
        String codeVerifier) {}
