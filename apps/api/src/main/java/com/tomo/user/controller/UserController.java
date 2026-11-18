package com.tomo.user.controller;

import com.tomo.common.response.CommonResponse;
import com.tomo.user.controller.docs.UserApiDocs;
import com.tomo.user.dto.response.UserProfileResponse;
import com.tomo.user.service.UserProfileQueryService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 회원 계정 및 프로필 API를 처리한다. */
@RestController
@RequestMapping("/api/v1/users")
public class UserController implements UserApiDocs {

  private final UserProfileQueryService userProfileQueryService;

  /**
   * 프로필 조회 서비스를 주입받는다.
   *
   * @param userProfileQueryService 프로필 조회 서비스
   */
  public UserController(UserProfileQueryService userProfileQueryService) {
    this.userProfileQueryService = userProfileQueryService;
  }

  /**
   * 현재 인증된 사용자의 계정 및 프로필을 조회한다.
   *
   * @param userId 인증된 사용자 ID
   * @param request HTTP 요청
   * @return 프로필 조회 응답
   */
  @Override
  @GetMapping("/me")
  public CommonResponse<UserProfileResponse> getMyProfile(
      @AuthenticationPrincipal String userId, HttpServletRequest request) {

    UserProfileResponse response =
        UserProfileResponse.from(userProfileQueryService.getMyProfile(userId));

    return CommonResponse.success(response, request.getRequestId());
  }
}
