package com.tomo.user.controller;

import com.tomo.common.response.CommonResponse;
import com.tomo.user.controller.docs.UserApiDocs;
import com.tomo.user.dto.response.UserProfileResponse;
import com.tomo.user.service.UserProfileFacade;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 회원 계정 및 프로필 API를 처리한다. */
@RestController
@RequestMapping("/api/v1/users")
public class UserController implements UserApiDocs {
  private static final Logger LOG = LoggerFactory.getLogger(UserController.class);

  private final UserProfileFacade userProfileFacade;

  /**
   * 프로필 조회 Facade를 주입받는다.
   *
   * @param userProfileFacade 프로필 조회 Facade
   */
  public UserController(UserProfileFacade userProfileFacade) {
    this.userProfileFacade = userProfileFacade;
  }

  /**
   * 현재 인증된 사용자의 계정 및 프로필을 조회한다.
   *
   * @param userId 인증된 사용자 ID
   * @param request HTTP 요청
   * @return 프로필 조회 응답
   */
  @Override
  @GetMapping(value = "/me", produces = MediaType.APPLICATION_JSON_VALUE)
  public CommonResponse<UserProfileResponse> getMyProfile(
      @AuthenticationPrincipal String userId, HttpServletRequest request) {

    UserProfileResponse response = UserProfileResponse.from(userProfileFacade.getMyProfile(userId));

    LOG.debug("본인 프로필 조회 완료");

    return CommonResponse.success(response, request.getRequestId());
  }
}
