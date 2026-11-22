package com.tomo.user.controller.docs;

import com.tomo.common.openapi.OpenApiConfig;
import com.tomo.common.response.CommonResponse;
import com.tomo.common.response.ErrorResponse;
import com.tomo.user.dto.response.AuthRefreshResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;

/** 인증 토큰 재발급 및 로그아웃 API 명세. */
@Tag(name = "Auth", description = "Refresh Token 재발급 및 현재 세션 로그아웃")
public interface AuthApiDocs {
  /**
   * Refresh Token을 검증해 새 Access·Refresh Token을 발급한다.
   *
   * @param authorization Bearer Refresh Token 요청 헤더
   * @param request 요청 ID 확인용 HTTP 요청
   * @return 새 토큰 쌍
   */
  @Operation(
      summary = "인증 토큰 재발급",
      description = "Access JWT는 필요하지 않으며, Authorization 헤더의 Refresh Token을 교체합니다.",
      security = @SecurityRequirement(name = OpenApiConfig.REFRESH_BEARER_AUTH))
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "토큰 재발급 성공",
        useReturnTypeSchema = true,
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                examples =
                    @ExampleObject(
                        value =
                            """
                                            {"success":true,"data":{"accessToken":"ACCESS_JWT","refreshToken":"10.RANDOM_TOKEN_VALUE","accessTokenExpiresIn":3600,"refreshTokenExpiresIn":604800,"user":{"role":"USER"}},"meta":{"requestId":"example-request-id","timestamp":"2026-11-21T00:00:00Z"}}
                                            """))),
    @ApiResponse(
        responseCode = "401",
        description = "INVALID_REFRESH_TOKEN",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "403",
        description = "TOKEN_REFRESH_FORBIDDEN",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "500",
        description = "INTERNAL_SERVER_ERROR",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ErrorResponse.class)))
  })
  CommonResponse<AuthRefreshResponse> refresh(
      @Parameter(hidden = true) String authorization,
      @Parameter(hidden = true) HttpServletRequest request);

  /**
   * Access JWT에 연결된 현재 로그인 세션을 종료한다.
   *
   * @param userId 인증된 사용자 ID
   * @param authentication 검증된 Access JWT의 세션 정보
   * @param request 요청 ID 확인용 HTTP 요청
   * @return 데이터가 없는 성공 응답
   */
  @Operation(
      summary = "로그아웃",
      description = "현재 기기의 Refresh Token과 DB 로그인 세션을 폐기합니다.",
      security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "로그아웃 성공",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                examples =
                    @ExampleObject(
                        value =
                            """
                                            {"success":true,"meta":{"requestId":"example-request-id","timestamp":"2026-11-21T00:00:00Z"}}
                                            """))),
    @ApiResponse(
        responseCode = "401",
        description = "INVALID_ACCESS_TOKEN",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "403",
        description = "LOGOUT_FORBIDDEN",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "500",
        description = "INTERNAL_SERVER_ERROR",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ErrorResponse.class)))
  })
  CommonResponse<Void> logout(
      @Parameter(hidden = true) String userId,
      @Parameter(hidden = true) Authentication authentication,
      @Parameter(hidden = true) HttpServletRequest request);
}
