package com.tomo.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.tomo.common.exception.BusinessException;
import com.tomo.common.exception.CommonErrorCode;
import com.tomo.user.entity.AccountStatus;
import com.tomo.user.entity.User;
import com.tomo.user.exception.UserErrorCode;
import com.tomo.user.repository.UserRepository;
import com.tomo.user.service.model.UserProfileDetails;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserProfileQueryServiceTest {
  private static final String USER_ID = "user-id";
  private static final String IMAGE_URL = "https://cdn.example.com/profile/user01.png";

  @Mock private UserRepository userRepository;
  @Mock private User user;

  private UserProfileQueryService service;

  @BeforeEach
  void setUp() {
    service = new UserProfileQueryService(userRepository);
  }

  @Test
  @DisplayName("활성 회원의 닉네임과 프로필 이미지를 조회한다")
  void returnsActiveUserProfile() {
    // given
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    when(user.getAccountStatus()).thenReturn(AccountStatus.ACTIVE);
    when(user.getNickname()).thenReturn("집중토모");
    when(user.getProfileImage()).thenReturn(IMAGE_URL);

    // when
    UserProfileDetails result = service.getActiveUserProfile(USER_ID);

    // then
    assertThat(result).isEqualTo(new UserProfileDetails("집중토모", IMAGE_URL));
  }

  @ParameterizedTest
  @EnumSource(
      value = AccountStatus.class,
      names = {"PENDING", "VAN", "DELETE"})
  @DisplayName("활성 상태가 아닌 계정의 프로필 조회는 거부한다")
  void rejectsInactiveUser(AccountStatus status) {
    // given
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    when(user.getAccountStatus()).thenReturn(status);

    // when
    Throwable thrown = catchThrowable(() -> service.getActiveUserProfile(USER_ID));

    // then
    assertThat(thrown)
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(UserErrorCode.PROFILE_VIEW_FORBIDDEN));
  }

  @Test
  @DisplayName("존재하지 않는 사용자는 RESOURCE_NOT_FOUND를 반환한다")
  void rejectsMissingUser() {
    // given
    when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

    // when
    Throwable thrown = catchThrowable(() -> service.getActiveUserProfile(USER_ID));

    // then
    assertThat(thrown)
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(CommonErrorCode.RESOURCE_NOT_FOUND));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  @DisplayName("사용자 ID가 없거나 공백이면 저장소를 조회하지 않는다")
  void rejectsMissingUserId(String userId) {
    // when
    Throwable thrown = catchThrowable(() -> service.getActiveUserProfile(userId));

    // then
    assertThat(thrown)
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(CommonErrorCode.INVALID_ACCESS_TOKEN));

    verifyNoInteractions(userRepository);
  }
}
