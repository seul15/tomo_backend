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
@Tag(name = "Auth", description = "인증 토큰 재발급 및 현재 기기 로그아웃 API")
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
      description =
          "Authorization 헤더의 Bearer Refresh Token을 검증하여 "
              + "새 Access Token과 Refresh Token을 발급합니다. "
              + "Access Token은 필요하지 않으며, 별도의 Path·Query·Body 입력도 없습니다. "
              + "재발급 성공 시 기존 Refresh Token을 교체하고 로그인 세션 만료 시각을 "
              + "현재 시각으로부터 7일 연장합니다. "
              + "동일 세션의 중복 재발급에서는 먼저 교체한 요청만 성공합니다. "
              + "직전 토큰을 10초 이내에 다시 사용하면 재발급만 거부하고, "
              + "10초 이후 재사용이 확인되면 해당 기기의 로그인 세션을 무효화합니다.",
      security = @SecurityRequirement(name = OpenApiConfig.REFRESH_BEARER_AUTH))
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "인증 토큰 재발급 성공",
        useReturnTypeSchema = true,
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                examples =
                    @ExampleObject(
                        value =
                            """
                                            {
                                              "success": true,
                                              "data": {
                                                "accessToken": "eyJhbGciOiJIUzI1NiJ9.example",
                                                "refreshToken": "123.NEW_REFRESH_TOKEN",
                                                "accessTokenExpiresIn": 3600,
                                                "refreshTokenExpiresIn": 604800,
                                                "user": {
                                                  "role": "USER"
                                                }
                                              },
                                              "meta": {
                                                "requestId": "example-request-id",
                                                "timestamp": "2026-11-23T00:00:00Z"
                                              }
                                            }
                                            """))),
    @ApiResponse(
        responseCode = "401",
        description = "Refresh Token이 없거나 유효하지 않음",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ErrorResponse.class),
                examples =
                    @ExampleObject(
                        value =
                            """
                                            {
                                              "success": false,
                                              "error": {
                                                "code": "INVALID_REFRESH_TOKEN",
                                                "message": "Refresh Token이 없거나 형식이 올바르지 않거나, 만료·폐기·교체되어 사용할 수 없습니다."
                                              },
                                              "meta": {
                                                "requestId": "example-request-id",
                                                "timestamp": "2026-11-23T00:00:00Z"
                                              }
                                            }
                                            """))),
    @ApiResponse(
        responseCode = "403",
        description = "비활성화된 계정의 토큰 재발급 거부",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ErrorResponse.class),
                examples =
                    @ExampleObject(
                        value =
                            """
                                            {
                                              "success": false,
                                              "error": {
                                                "code": "TOKEN_REFRESH_FORBIDDEN",
                                                "message": "비활성화된 계정은 인증 토큰을 재발급할 수 없습니다."
                                              },
                                              "meta": {
                                                "requestId": "example-request-id",
                                                "timestamp": "2026-11-23T00:00:00Z"
                                              }
                                            }
                                            """))),
    @ApiResponse(
        responseCode = "409",
        description = "동일 로그인 세션 요청 경합으로 분산 락 획득 실패",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ErrorResponse.class),
                examples =
                    @ExampleObject(
                        value =
                            """
                                          {
                                            "success": false,
                                            "error": {
                                              "code": "SESSION_STATE_CONFLICT",
                                              "message": "동일 세션의 로그아웃과 토큰 재발급이 동시에 처리되어 상태가 충돌했습니다."
                                            },
                                            "meta": {
                                              "requestId": "example-request-id",
                                              "timestamp": "2026-11-23T00:00:00Z"
                                            }
                                          }
                                          """))),
    @ApiResponse(
        responseCode = "500",
        description = "서버 내부 오류",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ErrorResponse.class),
                examples =
                    @ExampleObject(
                        value =
                            """
                                            {
                                              "success": false,
                                              "error": {
                                                "code": "INTERNAL_SERVER_ERROR",
                                                "message": "서버 처리 중 오류가 발생했습니다."
                                              },
                                              "meta": {
                                                "requestId": "example-request-id",
                                                "timestamp": "2026-11-23T00:00:00Z"
                                              }
                                            }
                                            """)))
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
      description =
          "Access Token으로 인증된 현재 기기의 로그인 세션을 종료합니다. "
              + "Authorization 헤더에 Bearer Access Token이 필요하며, "
              + "별도의 Path·Query·Body 입력은 필요하지 않습니다. "
              + "해당 세션의 Refresh Token을 Redis에서 폐기하고 "
              + "DB 로그인 세션을 삭제합니다. "
              + "로그아웃 후 해당 세션의 Access Token과 Refresh Token은 사용할 수 없으며, "
              + "다른 기기의 로그인 세션에는 영향을 주지 않습니다. "
              + "클라이언트는 성공 응답을 받은 후 보관 중인 두 토큰을 삭제해야 합니다.",
      security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "로그아웃 성공. data 필드 없이 success와 meta만 반환",
        useReturnTypeSchema = true,
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                examples =
                    @ExampleObject(
                        value =
                            """
                                            {
                                              "success": true,
                                              "meta": {
                                                "requestId": "example-request-id",
                                                "timestamp": "2026-11-23T00:00:00Z"
                                              }
                                            }
                                            """))),
    @ApiResponse(
        responseCode = "401",
        description = "Access Token이 없거나 유효하지 않음",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ErrorResponse.class),
                examples =
                    @ExampleObject(
                        value =
                            """
                                            {
                                              "success": false,
                                              "error": {
                                                "code": "INVALID_ACCESS_TOKEN",
                                                "message": "accessToken이 없거나 형식이 올바르지 않거나 만료·폐기되었습니다."
                                              },
                                              "meta": {
                                                "requestId": "example-request-id",
                                                "timestamp": "2026-11-23T00:00:00Z"
                                              }
                                            }
                                            """))),
    @ApiResponse(
        responseCode = "409",
        description = "동일 로그인 세션 요청 경합으로 분산 락 획득 실패",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ErrorResponse.class),
                examples =
                    @ExampleObject(
                        value =
                            """
                                          {
                                            "success": false,
                                            "error": {
                                              "code": "SESSION_STATE_CONFLICT",
                                              "message": "동일 세션의 로그아웃과 토큰 재발급이 동시에 처리되어 상태가 충돌했습니다."
                                            },
                                            "meta": {
                                              "requestId": "example-request-id",
                                              "timestamp": "2026-11-23T00:00:00Z"
                                            }
                                          }
                                          """))),
    @ApiResponse(
        responseCode = "500",
        description = "서버 내부 오류",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ErrorResponse.class),
                examples =
                    @ExampleObject(
                        value =
                            """
                                            {
                                              "success": false,
                                              "error": {
                                                "code": "INTERNAL_SERVER_ERROR",
                                                "message": "서버 처리 중 오류가 발생했습니다."
                                              },
                                              "meta": {
                                                "requestId": "example-request-id",
                                                "timestamp": "2026-11-23T00:00:00Z"
                                              }
                                            }
                                            """)))
  })
  CommonResponse<Void> logout(
      @Parameter(hidden = true) String userId,
      @Parameter(hidden = true) Authentication authentication,
      @Parameter(hidden = true) HttpServletRequest request);
}
