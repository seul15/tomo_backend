package com.tomo.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** 신규·가입 대기 응답과 로그인 완료 응답의 공통 HTTP 결과 계약. */
@Schema(
    description = "가입 대기 결과 또는 로그인 완료 결과 중 하나를 반환",
    oneOf = {SignupPendingResponse.class, AuthenticatedUserResponse.class})
public sealed interface SocialLoginResponse
    permits SignupPendingResponse, AuthenticatedUserResponse {}
