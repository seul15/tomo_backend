package com.tomo.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.when;

import com.tomo.common.exception.BusinessException;
import com.tomo.common.exception.CommonErrorCode;
import com.tomo.user.entity.SocialIdentity;
import com.tomo.user.entity.SocialProvider;
import com.tomo.user.repository.SocialIdentityRepository;
import com.tomo.user.service.model.SocialIdentityDetails;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SocialIdentityQueryServiceTest {
  private static final String USER_ID = "user-id";

  @Mock private SocialIdentityRepository socialIdentityRepository;
  @Mock private SocialIdentity socialIdentity;

  private SocialIdentityQueryService service;

  @BeforeEach
  void setUp() {
    service = new SocialIdentityQueryService(socialIdentityRepository);
  }

  @Test
  @DisplayName("연결된 구글 계정의 이메일과 제공자를 조회한다")
  void returnsGoogleSocialIdentity() {
    // given
    when(socialIdentityRepository.findByUserId(USER_ID)).thenReturn(Optional.of(socialIdentity));
    when(socialIdentity.getEmail()).thenReturn("test@google.com");
    when(socialIdentity.getProvider()).thenReturn(SocialProvider.GOOGLE);

    // when
    SocialIdentityDetails result = service.getProfileDetailsByUserId(USER_ID);

    // then
    assertThat(result)
        .isEqualTo(new SocialIdentityDetails("test@google.com", SocialProvider.GOOGLE));
  }

  @Test
  @DisplayName("이메일이 없는 카카오 계정도 정상적으로 조회한다")
  void returnsKakaoSocialIdentityWithoutEmail() {
    // given
    when(socialIdentityRepository.findByUserId(USER_ID)).thenReturn(Optional.of(socialIdentity));
    when(socialIdentity.getProvider()).thenReturn(SocialProvider.KAKAO);

    // when
    SocialIdentityDetails result = service.getProfileDetailsByUserId(USER_ID);

    // then
    assertThat(result.email()).isNull();
    assertThat(result.provider()).isEqualTo(SocialProvider.KAKAO);
  }

  @Test
  @DisplayName("소셜 계정 연결 정보가 없으면 RESOURCE_NOT_FOUND를 반환한다")
  void rejectsMissingSocialIdentity() {
    // given
    when(socialIdentityRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

    // when
    Throwable thrown = catchThrowable(() -> service.getProfileDetailsByUserId(USER_ID));

    // then
    assertThat(thrown)
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(CommonErrorCode.RESOURCE_NOT_FOUND));
  }
}
