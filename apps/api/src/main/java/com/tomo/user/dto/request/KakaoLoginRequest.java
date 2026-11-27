package com.tomo.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Kakao Authorization Code를 서버에 전달하는 로그인 요청.
 *
 * <p>프론트엔드에서 OAuth state를 확인한 후 전달하는 인가 코드다.
 *
 * @param authorizationCode Kakao에서 발급받은 인가 코드
 */
@Schema(description = "Kakao 소셜 로그인 요청")
public record KakaoLoginRequest(
    @Schema(
            description = "Kakao 인증 완료 후 발급된 일회용 Authorization Code",
            example = "kakao-authorization-code",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String authorizationCode) {}
