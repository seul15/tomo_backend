package com.tomo.user.controller.docs;

import com.tomo.common.openapi.OpenApiConfig;
import com.tomo.common.response.CommonResponse;
import com.tomo.common.response.ErrorResponse;
import com.tomo.user.dto.request.GoogleLoginRequest;
import com.tomo.user.dto.request.KakaoLoginRequest;
import com.tomo.user.dto.response.AuthRefreshResponse;
import com.tomo.user.dto.response.SocialLoginResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;

/** Google·Kakao 로그인, 인증 토큰 재발급 및 로그아웃 API 명세. */
@Tag(name = "Auth", description = "Google·Kakao 소셜 로그인, 토큰 재발급 및 로그아웃 API")
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
                                                "refreshToken": "123.AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
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

  /**
   * Google에서 검증한 사용자 고유 ID를 기준으로 소셜 로그인을 처리한다.
   *
   * <p>가입 대기 회원에게는 Signup Token만, 가입 완료 회원에게는 Access Token과 Refresh Token을 반환한다.
   *
   * @param body Google 인가 코드와 PKCE 검증값
   * @param request 요청 ID 확인용 HTTP 요청
   * @return 회원 가입 상태에 따른 소셜 로그인 결과
   */
  @Operation(
      summary = "Google 소셜 로그인",
      description =
          "Google Authorization Code와 PKCE codeVerifier로 사용자를 인증합니다. "
              + "프론트엔드에서 OAuth state를 검증한 후 인가 코드와 codeVerifier를 전달합니다. "
              + "백엔드는 Google 토큰 API를 통해 PKCE를 검증받고 사용자 고유 ID(sub)를 조회합니다. "
              + "이메일은 계정 식별이나 저장에 사용하지 않습니다. "
              + "신규 회원 또는 PENDING 회원에게는 회원가입용 Signup Token을 반환하며, "
              + "ACTIVE 회원에게는 새 로그인 세션을 생성하고 Access Token과 Refresh Token을 반환합니다. "
              + "BAN 회원은 로그인을 거부하고, DELETE 회원은 기존 계정을 복구하지 않고 신규 가입으로 처리합니다. "
              + "Access Token 인증은 필요하지 않습니다.",
      requestBody =
          @RequestBody(
              description = "Google 인가 코드와 PKCE 검증값",
              required = true,
              content =
                  @Content(
                      mediaType = MediaType.APPLICATION_JSON_VALUE,
                      schema = @Schema(implementation = GoogleLoginRequest.class),
                      examples =
                          @ExampleObject(
                              name = "Google 로그인 요청",
                              value =
                                  """
                                            {
                                              "authorizationCode": "google-authorization-code",
                                              "codeVerifier": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                                            }
                                            """))))
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "가입 대기 또는 로그인 완료",
        useReturnTypeSchema = true,
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                examples = {
                  @ExampleObject(
                      name = "가입 대기 - PENDING",
                      value =
                          """
                                                    {
                                                      "success": true,
                                                      "data": {
                                                        "signupToken": "eyJhbGciOiJIUzI1NiJ9.example",
                                                        "signupTokenExpiresIn": 600
                                                      },
                                                      "meta": {
                                                        "requestId": "example-request-id",
                                                        "timestamp": "2026-11-27T00:00:00Z"
                                                      }
                                                    }
                                                    """),
                  @ExampleObject(
                      name = "로그인 완료 - ACTIVE",
                      value =
                          """
                                                    {
                                                      "success": true,
                                                      "data": {
                                                        "accessToken": "eyJhbGciOiJIUzI1NiJ9.example",
                                                        "refreshToken": "123.AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
                                                        "accessTokenExpiresIn": 3600,
                                                        "refreshTokenExpiresIn": 604800,
                                                        "user": {
                                                          "role": "USER",
                                                          "nickname": "집중토모",
                                                          "profileImageUrl": "https://res.cloudinary.com/example/default.png"
                                                        }
                                                      },
                                                      "meta": {
                                                        "requestId": "example-request-id",
                                                        "timestamp": "2026-11-27T00:00:00Z"
                                                      }
                                                    }
                                                    """)
                })),
    @ApiResponse(
        responseCode = "400",
        description = "인가 코드 누락 또는 PKCE 검증값 형식 오류",
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
                                                "code": "INVALID_REQUEST",
                                                "message": "요청 필드 또는 도메인 검증 조건이 올바르지 않습니다."
                                              },
                                              "meta": {
                                                "requestId": "example-request-id",
                                                "timestamp": "2026-11-27T00:00:00Z"
                                              }
                                            }
                                            """))),
    @ApiResponse(
        responseCode = "401",
        description = "Google 인증 실패 또는 인가 코드 만료",
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
                                                "code": "OAUTH_AUTHENTICATION_FAILED",
                                                "message": "소셜 인증에 실패했거나 인가 코드가 만료되었습니다."
                                              },
                                              "meta": {
                                                "requestId": "example-request-id",
                                                "timestamp": "2026-11-27T00:00:00Z"
                                              }
                                            }
                                            """))),
    @ApiResponse(
        responseCode = "403",
        description = "BAN 상태 계정의 로그인 거부",
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
                                                "code": "SOCIAL_LOGIN_FORBIDDEN",
                                                "message": "이용이 제한된 계정은 소셜 로그인을 진행할 수 없습니다."
                                              },
                                              "meta": {
                                                "requestId": "example-request-id",
                                                "timestamp": "2026-11-27T00:00:00Z"
                                              }
                                            }
                                            """))),
    @ApiResponse(
        responseCode = "409",
        description = "동시 로그인으로 소셜 계정 연결 충돌",
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
                                                "code": "CONCURRENT_REQUEST_CONFLICT",
                                                "message": "동일한 리소스에 대한 요청이 동시에 처리되어 충돌이 발생했습니다."
                                              },
                                              "meta": {
                                                "requestId": "example-request-id",
                                                "timestamp": "2026-11-27T00:00:00Z"
                                              }
                                            }
                                            """))),
    @ApiResponse(
        responseCode = "500",
        description = "OAuth 제공자 통신 오류 또는 서버 내부 오류",
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
                                                "timestamp": "2026-11-27T00:00:00Z"
                                              }
                                            }
                                            """)))
  })
  CommonResponse<SocialLoginResponse> google(
      GoogleLoginRequest body, @Parameter(hidden = true) HttpServletRequest request);

  /**
   * Kakao에서 검증한 사용자 고유 ID를 기준으로 소셜 로그인을 처리한다.
   *
   * <p>인가 코드로 사용자를 인증한 뒤 가입 상태에 따라 Signup Token 또는 로그인 토큰 쌍을 반환한다.
   *
   * @param body Kakao 인가 코드
   * @param request 요청 ID 확인용 HTTP 요청
   * @return 회원 가입 상태에 따른 소셜 로그인 결과
   */
  @Operation(
      summary = "Kakao 소셜 로그인",
      description =
          "Kakao Authorization Code로 사용자를 인증합니다. "
              + "프론트엔드에서 OAuth state를 검증한 후 인가 코드를 전달합니다. "
              + "백엔드는 Kakao 토큰 API에서 Access Token을 발급받고 사용자 고유 ID를 조회합니다. "
              + "이메일은 계정 식별이나 저장에 사용하지 않습니다. "
              + "신규 회원 또는 PENDING 회원은 Signup Token을 발급받아 회원가입을 진행합니다. "
              + "ACTIVE 회원은 새 로그인 세션과 Access Token, Refresh Token을 발급받습니다. "
              + "BAN 회원은 로그인이 거부되며, DELETE 회원은 신규 계정으로 다시 가입합니다. "
              + "Access Token 인증은 필요하지 않습니다.",
      requestBody =
          @RequestBody(
              description = "Kakao 인가 코드",
              required = true,
              content =
                  @Content(
                      mediaType = MediaType.APPLICATION_JSON_VALUE,
                      schema = @Schema(implementation = KakaoLoginRequest.class),
                      examples =
                          @ExampleObject(
                              name = "Kakao 로그인 요청",
                              value =
                                  """
                                            {
                                              "authorizationCode": "kakao-authorization-code"
                                            }
                                            """))))
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "가입 대기 또는 로그인 완료",
        useReturnTypeSchema = true,
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                examples = {
                  @ExampleObject(
                      name = "가입 대기 - PENDING",
                      value =
                          """
                                                    {
                                                      "success": true,
                                                      "data": {
                                                        "signupToken": "eyJhbGciOiJIUzI1NiJ9.example",
                                                        "signupTokenExpiresIn": 600
                                                      },
                                                      "meta": {
                                                        "requestId": "example-request-id",
                                                        "timestamp": "2026-11-27T00:00:00Z"
                                                      }
                                                    }
                                                    """),
                  @ExampleObject(
                      name = "로그인 완료 - ACTIVE",
                      value =
                          """
                                                    {
                                                      "success": true,
                                                      "data": {
                                                        "accessToken": "eyJhbGciOiJIUzI1NiJ9.example",
                                                        "refreshToken": "123.AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
                                                        "accessTokenExpiresIn": 3600,
                                                        "refreshTokenExpiresIn": 604800,
                                                        "user": {
                                                          "role": "USER",
                                                          "nickname": "집중토모",
                                                          "profileImageUrl": "https://res.cloudinary.com/example/default.png"
                                                        }
                                                      },
                                                      "meta": {
                                                        "requestId": "example-request-id",
                                                        "timestamp": "2026-11-27T00:00:00Z"
                                                      }
                                                    }
                                                    """)
                })),
    @ApiResponse(
        responseCode = "400",
        description = "인가 코드 누락 또는 잘못된 요청",
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
                                                "code": "INVALID_REQUEST",
                                                "message": "요청 필드 또는 도메인 검증 조건이 올바르지 않습니다."
                                              },
                                              "meta": {
                                                "requestId": "example-request-id",
                                                "timestamp": "2026-11-27T00:00:00Z"
                                              }
                                            }
                                            """))),
    @ApiResponse(
        responseCode = "401",
        description = "Kakao 인증 실패 또는 인가 코드 만료",
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
                                                "code": "OAUTH_AUTHENTICATION_FAILED",
                                                "message": "소셜 인증에 실패했거나 인가 코드가 만료되었습니다."
                                              },
                                              "meta": {
                                                "requestId": "example-request-id",
                                                "timestamp": "2026-11-27T00:00:00Z"
                                              }
                                            }
                                            """))),
    @ApiResponse(
        responseCode = "403",
        description = "BAN 상태 계정의 로그인 거부",
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
                                                "code": "SOCIAL_LOGIN_FORBIDDEN",
                                                "message": "이용이 제한된 계정은 소셜 로그인을 진행할 수 없습니다."
                                              },
                                              "meta": {
                                                "requestId": "example-request-id",
                                                "timestamp": "2026-11-27T00:00:00Z"
                                              }
                                            }
                                            """))),
    @ApiResponse(
        responseCode = "409",
        description = "동시 로그인으로 소셜 계정 연결 충돌",
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
                                                "code": "CONCURRENT_REQUEST_CONFLICT",
                                                "message": "동일한 리소스에 대한 요청이 동시에 처리되어 충돌이 발생했습니다."
                                              },
                                              "meta": {
                                                "requestId": "example-request-id",
                                                "timestamp": "2026-11-27T00:00:00Z"
                                              }
                                            }
                                            """))),
    @ApiResponse(
        responseCode = "500",
        description = "OAuth 제공자 통신 오류 또는 서버 내부 오류",
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
                                                "timestamp": "2026-11-27T00:00:00Z"
                                              }
                                            }
                                            """)))
  })
  CommonResponse<SocialLoginResponse> kakao(
      KakaoLoginRequest body, @Parameter(hidden = true) HttpServletRequest request);
}
