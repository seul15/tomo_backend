package com.tomo.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.tomo.common.exception.BusinessException;
import com.tomo.common.exception.CommonErrorCode;
import com.tomo.user.entity.SocialProvider;
import com.tomo.user.exception.UserErrorCode;
import com.tomo.user.service.model.SocialIdentityDetails;
import com.tomo.user.service.model.UserProfileDetails;
import com.tomo.user.service.model.UserProfileResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserProfileFacadeTest {
  private static final String USER_ID = "user-id";
  private static final String IMAGE_URL = "https://cdn.example.com/profile/user01.png";

  @Mock private UserProfileQueryService userProfileQueryService;
  @Mock private SocialIdentityQueryService socialIdentityQueryService;

  private UserProfileFacade facade;

  @BeforeEach
  void setUp() {
    facade = new UserProfileFacade(userProfileQueryService, socialIdentityQueryService);
  }

  @Test
  @DisplayName("회원 정보와 소셜 계정 정보를 조합하여 전체 프로필을 반환한다")
  void returnsOwnProfile() {
    // given
    when(userProfileQueryService.getActiveUserProfile(USER_ID))
        .thenReturn(new UserProfileDetails("집중토모", IMAGE_URL));

    when(socialIdentityQueryService.getProfileDetailsByUserId(USER_ID))
        .thenReturn(new SocialIdentityDetails("test@google.com", SocialProvider.GOOGLE));

    // when
    UserProfileResult result = facade.getMyProfile(USER_ID);

    // then
    assertThat(result)
        .isEqualTo(
            new UserProfileResult("집중토모", IMAGE_URL, "test@google.com", SocialProvider.GOOGLE));

    verify(userProfileQueryService).getActiveUserProfile(USER_ID);
    verify(socialIdentityQueryService).getProfileDetailsByUserId(USER_ID);
  }

  @Test
  @DisplayName("소셜 계정 이메일이 없어도 전체 프로필을 반환한다")
  void returnsProfileWithoutEmail() {
    // given
    when(userProfileQueryService.getActiveUserProfile(USER_ID))
        .thenReturn(new UserProfileDetails("집중토모", IMAGE_URL));

    when(socialIdentityQueryService.getProfileDetailsByUserId(USER_ID))
        .thenReturn(new SocialIdentityDetails(null, SocialProvider.KAKAO));

    // when
    UserProfileResult result = facade.getMyProfile(USER_ID);

    // then
    assertThat(result.nickname()).isEqualTo("집중토모");
    assertThat(result.profileImageUrl()).isEqualTo(IMAGE_URL);
    assertThat(result.email()).isNull();
    assertThat(result.provider()).isEqualTo(SocialProvider.KAKAO);
  }

  @Test
  @DisplayName("회원이 존재하지 않으면 소셜 계정을 조회하지 않는다")
  void rejectsMissingUser() {
    // given
    when(userProfileQueryService.getActiveUserProfile(USER_ID))
        .thenThrow(new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));

    // when
    Throwable thrown = catchThrowable(() -> facade.getMyProfile(USER_ID));

    // then
    assertThat(thrown)
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(CommonErrorCode.RESOURCE_NOT_FOUND));

    verifyNoInteractions(socialIdentityQueryService);
  }

  @Test
  @DisplayName("활성 상태가 아닌 회원은 소셜 계정을 조회하지 않는다")
  void rejectsInactiveUser() {
    // given
    when(userProfileQueryService.getActiveUserProfile(USER_ID))
        .thenThrow(new BusinessException(UserErrorCode.PROFILE_VIEW_FORBIDDEN));

    // when
    Throwable thrown = catchThrowable(() -> facade.getMyProfile(USER_ID));

    // then
    assertThat(thrown)
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(UserErrorCode.PROFILE_VIEW_FORBIDDEN));

    verifyNoInteractions(socialIdentityQueryService);
  }

  @Test
  @DisplayName("소셜 계정 연결 정보가 없으면 RESOURCE_NOT_FOUND를 반환한다")
  void rejectsMissingSocialIdentity() {
    // given
    when(userProfileQueryService.getActiveUserProfile(USER_ID))
        .thenReturn(new UserProfileDetails("집중토모", IMAGE_URL));

    when(socialIdentityQueryService.getProfileDetailsByUserId(USER_ID))
        .thenThrow(new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));

    // when
    Throwable thrown = catchThrowable(() -> facade.getMyProfile(USER_ID));

    // then
    assertThat(thrown)
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(CommonErrorCode.RESOURCE_NOT_FOUND));

    verify(userProfileQueryService).getActiveUserProfile(USER_ID);
  }
}
