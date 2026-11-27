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
          """
                  Refresh Token을 검증하여 새로운 Access Token과 Refresh Token을 발급합니다.

                  ### 요청 방법
                  - `POST /api/v1/auth/refresh`
                  - `Authorization: Bearer {refreshToken}` 헤더가 필요합니다.
                  - Access Token 인증은 필요하지 않습니다.
                  - Path, Query, Body 입력값은 없습니다.
                  - Refresh Token은 쿠키가 아닌 Authorization 헤더로 전달합니다.

                  ### 토큰 검증 및 재발급 규칙
                  - Refresh Token의 형식과 로그인 세션 ID를 확인합니다.
                  - DB 로그인 세션의 존재 여부와 만료 시각을 검증합니다.
                  - Redis에 저장된 Refresh Token 상태를 검증합니다.
                  - ACTIVE 상태의 회원만 재발급할 수 있습니다.
                  - 성공하면 Access Token과 Refresh Token을 모두 새로 발급합니다.
                  - 기존 Refresh Token은 교체되어 더 이상 사용할 수 없습니다.
                  - 로그인 세션 만료 시각을 재발급 시점으로부터 7일 연장합니다.

                  ### 토큰 만료기간
                  - `accessTokenExpiresIn`: Access Token 유효기간(초). 기본값 3600초
                  - `refreshTokenExpiresIn`: Refresh Token 유효기간(초). 기본값 604800초
                  - 두 값은 고정 날짜가 아닌 유효기간을 초 단위로 나타냅니다.

                  ### 동시 요청 및 토큰 재사용
                  - 동일 세션의 재발급과 로그아웃 요청은 분산 락으로 직렬화합니다.
                  - 분산 락을 5초 안에 획득하지 못하면 `409 SESSION_STATE_CONFLICT`를 반환합니다.
                  - 직전 Refresh Token을 교체 후 10초 이내에 재사용하면 재발급만 거부합니다.
                  - 교체 후 10초를 초과하여 직전 토큰을 재사용하면 로그인 세션을 무효화합니다.
                  - 단, 요청 자체의 처리 지연 때문에 재사용으로 판정된 경우에는 세션 폐기를 보류합니다.

                  ### 성공 응답 및 프론트 연동
                  - HTTP 200 응답의 `data`에 새로운 `accessToken`, `refreshToken`이 포함됩니다.
                  - 각 토큰의 만료기간과 `user.role`도 반환합니다.
                  - 프론트에서는 성공 응답을 받은 후 기존 토큰 두 개를 모두 새 값으로 교체해야 합니다.
                  - 이미 교체된 Refresh Token으로 재발급을 다시 요청하지 않도록 주의해야 합니다.

                  ### 오류 응답
                  - `401 INVALID_REFRESH_TOKEN`: 토큰 누락, 형식 오류, 만료, 폐기 또는 교체된 토큰 사용
                  - `403 TOKEN_REFRESH_FORBIDDEN`: 비활성화된 회원
                  - `409 SESSION_STATE_CONFLICT`: 동일 세션의 요청 경합
                  - `500 INTERNAL_SERVER_ERROR`: 서버 내부 처리 오류
                  """,
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
                                                "refreshToken": "123.AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
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
          """
                  Access Token으로 인증된 사용자의 현재 로그인 세션을 종료합니다.

                  ### 요청 방법
                  - `POST /api/v1/auth/logout`
                  - `Authorization: Bearer {accessToken}` 헤더가 필요합니다.
                  - Refresh Token은 요청에 전달하지 않습니다.
                  - Path, Query, Body 입력값은 없습니다.

                  ### 처리 규칙
                  - Access Token과 연결된 DB 로그인 세션을 확인합니다.
                  - 요청한 사용자가 해당 로그인 세션의 소유자인지 검증합니다.
                  - DB 로그인 세션을 삭제한 후 Redis의 Refresh Token을 폐기합니다.
                  - Redis 토큰 폐기는 DB 세션 삭제 트랜잭션이 커밋된 뒤 수행합니다.
                  - Redis 폐기에 실패하더라도 DB 세션 삭제가 완료되면 로그아웃은 성공 처리합니다.
                  - 현재 로그인 세션만 종료하며 다른 기기의 로그인 세션에는 영향을 주지 않습니다.

                  ### 성공 응답 및 프론트 연동
                  - 성공 시 HTTP 200을 반환합니다.
                  - 응답에는 `success`와 `meta`만 포함되고 `data` 필드는 없습니다.
                  - 성공 응답을 받은 뒤 프론트에서 보관 중인 Access Token과 Refresh Token을 삭제해야 합니다.
                  - 종료된 세션에 연결된 기존 토큰은 더 이상 사용할 수 없습니다.

                  ### 오류 응답
                  - `401 INVALID_ACCESS_TOKEN`: Access Token 누락, 형식 오류, 만료 또는 무효 세션
                  - `409 SESSION_STATE_CONFLICT`: 동일 세션의 재발급·로그아웃 요청 경합
                  - `500 INTERNAL_SERVER_ERROR`: 서버 내부 처리 오류
                  """,
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
          """
                  Google Authorization Code와 PKCE 검증값으로 사용자를 인증하고 소셜 로그인을 처리합니다.

                  ### 요청 방법
                  - `POST /api/v1/auth/google`
                  - `Content-Type: application/json`
                  - Access Token 인증은 필요하지 않습니다.
                  - 프론트에서 OAuth state를 검증한 후 인가 코드와 codeVerifier를 전달합니다.
                  - 백엔드는 state를 요청받거나 직접 검증하지 않습니다.

                  ### 요청 필드
                  - `authorizationCode`: Google에서 발급받은 인가 코드입니다. 필수값이며 빈 문자열을 허용하지 않습니다.
                  - `codeVerifier`: Google PKCE(S256) 인증에 사용한 원본 검증값입니다.
                  - `codeVerifier`는 43~128자여야 합니다.
                  - 허용 문자는 영문 대소문자, 숫자, `.`, `_`, `~`, `-`입니다.
                  - 요청값이 누락되거나 검증 조건을 충족하지 못하면 `400 INVALID_REQUEST`를 반환합니다.

                  ### 소셜 인증 및 계정 식별
                  - 백엔드는 인가 코드와 codeVerifier를 Google 토큰 API에 전달합니다.
                  - 발급받은 Google Access Token으로 사용자 정보를 조회합니다.
                  - Google 사용자 정보의 `sub`를 소셜 계정 고유 ID로 사용합니다.
                  - `provider=GOOGLE`과 고유 ID의 조합으로 기존 계정을 조회합니다.
                  - 소셜 이메일은 계정 식별 또는 저장에 사용하지 않습니다.

                  ### 회원 상태별 처리
                  - 신규 회원: PENDING 계정과 소셜 연결을 생성합니다.
                  - PENDING 회원: 기존 계정을 사용하고 회원가입용 Signup Token을 발급합니다.
                  - ACTIVE 회원: 새로운 로그인 세션을 만들고 Access Token과 Refresh Token을 발급합니다.
                  - BAN 회원: `403 SOCIAL_LOGIN_FORBIDDEN`을 반환합니다.
                  - DELETE 회원: 기존 계정을 복구하지 않고 새로운 PENDING 계정을 생성합니다.

                  ### 성공 응답 및 프론트 연동
                  - 신규 회원과 기존 회원 모두 HTTP 200을 반환합니다.
                  - 가입 대기 응답에는 `signupToken`, `signupTokenExpiresIn`만 포함됩니다.
                  - 가입 대기 응답에는 Access Token과 Refresh Token이 포함되지 않습니다.
                  - 로그인 완료 응답에는 `accessToken`, `refreshToken`, 각 토큰의 만료기간과 `user`가 포함됩니다.
                  - `user`에는 `role`, `nickname`, `profileImageUrl`이 포함됩니다.
                  - 로그인 시 저장된 프로필 이미지가 없으면 설정된 기본 프로필 이미지 URL을 사용합니다.
                  - 프론트에서는 `data.signupToken` 또는 `data.accessToken`의 존재 여부로 결과를 구분할 수 있습니다.
                  - 로그인 완료 후 Access Token을 인증이 필요한 API의 Bearer 토큰으로 사용합니다.

                  ### 오류 응답
                  - `400 INVALID_REQUEST`: 인가 코드 누락 또는 PKCE 검증값 형식 오류
                  - `401 OAUTH_AUTHENTICATION_FAILED`: Google 인증 실패 또는 인가 코드 만료
                  - `403 SOCIAL_LOGIN_FORBIDDEN`: 이용 제한 계정
                  - `409 CONCURRENT_REQUEST_CONFLICT`: 동시 소셜 계정 연결 충돌
                  - `500 INTERNAL_SERVER_ERROR`: 제공자 통신 장애, 설정 오류 또는 서버 내부 오류
                  """,
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
                                                        "refreshToken": "123.AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
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
          """
                  Kakao Authorization Code로 사용자를 인증하고 소셜 로그인을 처리합니다.

                  ### 요청 방법
                  - `POST /api/v1/auth/kakao`
                  - `Content-Type: application/json`
                  - Access Token 인증은 필요하지 않습니다.
                  - 프론트에서 OAuth state를 검증한 후 인가 코드를 전달합니다.
                  - 백엔드는 state를 요청받거나 직접 검증하지 않습니다.

                  ### 요청 필드
                  - `authorizationCode`: Kakao에서 발급받은 인가 코드입니다.
                  - 필수값이며 null, 빈 문자열, 공백 문자열을 허용하지 않습니다.
                  - Google과 달리 `codeVerifier`를 요청받지 않습니다.
                  - 요청값이 올바르지 않으면 `400 INVALID_REQUEST`를 반환합니다.

                  ### 소셜 인증 및 계정 식별
                  - 백엔드는 인가 코드를 Kakao 토큰 API에 전달합니다.
                  - 발급받은 Kakao Access Token으로 사용자 정보를 조회합니다.
                  - 사용자 정보의 `id`를 문자열로 변환하여 소셜 계정 고유 ID로 사용합니다.
                  - `provider=KAKAO`와 고유 ID의 조합으로 기존 계정을 조회합니다.
                  - 소셜 이메일은 계정 식별 또는 저장에 사용하지 않습니다.

                  ### 회원 상태별 처리
                  - 신규 회원: PENDING 계정과 소셜 연결을 생성합니다.
                  - PENDING 회원: 기존 계정을 사용하고 회원가입용 Signup Token을 발급합니다.
                  - ACTIVE 회원: 새로운 로그인 세션을 만들고 Access Token과 Refresh Token을 발급합니다.
                  - BAN 회원: `403 SOCIAL_LOGIN_FORBIDDEN`을 반환합니다.
                  - DELETE 회원: 기존 계정을 복구하지 않고 새로운 PENDING 계정을 생성합니다.

                  ### 성공 응답 및 프론트 연동
                  - 가입 대기와 로그인 완료 모두 HTTP 200을 반환합니다.
                  - 가입 대기: `signupToken`, `signupTokenExpiresIn`을 반환합니다.
                  - 로그인 완료: `accessToken`, `refreshToken`, 각 만료기간과 `user`를 반환합니다.
                  - `user`에는 `role`, `nickname`, `profileImageUrl`이 포함됩니다.
                  - 저장된 프로필 이미지가 없으면 설정된 기본 프로필 이미지 URL을 사용합니다.
                  - 프론트에서는 `data.signupToken` 또는 `data.accessToken`의 존재 여부로 결과를 구분할 수 있습니다.
                  - 로그인 완료 후 Access Token을 인증이 필요한 API의 Bearer 토큰으로 사용합니다.

                  ### 오류 응답
                  - `400 INVALID_REQUEST`: 인가 코드 누락 또는 빈 값
                  - `401 OAUTH_AUTHENTICATION_FAILED`: Kakao 인증 실패 또는 인가 코드 만료
                  - `403 SOCIAL_LOGIN_FORBIDDEN`: 이용 제한 계정
                  - `409 CONCURRENT_REQUEST_CONFLICT`: 동시 소셜 계정 연결 충돌
                  - `500 INTERNAL_SERVER_ERROR`: 제공자 통신 장애, 설정 오류 또는 서버 내부 오류
                  """,
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
                                                        "refreshToken": "123.AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
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
