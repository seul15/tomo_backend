package com.tomo.user.controller.docs;

import com.tomo.common.openapi.OpenApiConfig;
import com.tomo.common.response.CommonResponse;
import com.tomo.common.response.ErrorResponse;
import com.tomo.user.dto.response.UserProfileResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;

/** 회원 계정 및 프로필 API 명세. */
@Tag(name = "User", description = "회원 계정 및 프로필 API")
public interface UserApiDocs {

  /**
   * 인증된 사용자의 프로필을 조회한다.
   *
   * @param userId 인증된 사용자 ID
   * @param request HTTP 요청
   * @return 내 계정 및 프로필 정보
   */
  @Operation(
      summary = "내 계정·프로필 조회",
      description =
          "Access Token으로 인증된 사용자 본인의 계정 및 프로필 정보를 조회합니다. "
              + "사용자 ID는 인증 정보에서 확인하며, 별도의 Path·Query·Body 입력은 필요하지 않습니다.",
      security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "계정 및 프로필 조회 성공"),
    @ApiResponse(
        responseCode = "401",
        description = "INVALID_ACCESS_TOKEN - Access Token이 없거나 형식이 올바르지 않거나 만료·폐기된 경우",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "403",
        description = "PROFILE_VIEW_FORBIDDEN - 해당 계정의 프로필을 조회할 권한이 없는 경우",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = "RESOURCE_NOT_FOUND - 조회할 리소스가 존재하지 않는 경우",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "500",
        description = "INTERNAL_SERVER_ERROR - 서버 처리 중 오류가 발생한 경우",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ErrorResponse.class)))
  })
  CommonResponse<UserProfileResponse> getMyProfile(
      @Parameter(hidden = true) String userId,
      @Parameter(hidden = true) HttpServletRequest request);
}
