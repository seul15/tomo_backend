package com.tomo.category.service;

import com.tomo.category.service.command.CreateCategoryCommand;
import com.tomo.category.service.command.UpdateCategoryCommand;
import com.tomo.category.service.model.CategoryListResult;
import com.tomo.common.auth.exception.AuthErrorCode;
import com.tomo.common.exception.BusinessException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 사용자 인증 식별자 확인과 카테고리 업무 서비스를 조정한다. */
@Service
public class CategoryFacade {
  private static final Logger LOG = LoggerFactory.getLogger(CategoryFacade.class);

  private final CategoryCommandService categoryCommandService;
  private final CategoryQueryService categoryQueryService;

  /**
   * 카테고리 생성·변경·조회 서비스를 주입받는다.
   *
   * @param categoryCommandService 카테고리 생성·변경 서비스
   * @param categoryQueryService 카테고리 조회 서비스
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Spring이 관리하는 서비스 빈을 공유 의존성으로 주입받는다.")
  public CategoryFacade(
      CategoryCommandService categoryCommandService, CategoryQueryService categoryQueryService) {
    this.categoryCommandService = categoryCommandService;
    this.categoryQueryService = categoryQueryService;
  }

  /**
   * 인증된 사용자의 공부 카테고리를 생성한다.
   *
   * @param command 카테고리 생성 입력 값
   * @throws BusinessException 인증 정보가 없거나, 카테고리 이름이 유효하지 않거나, 중복·동시 요청 충돌이 발생한 경우
   */
  @Transactional
  public void createCategory(CreateCategoryCommand command) {
    if (command == null || command.userId() == null || command.userId().isBlank()) {
      LOG.debug("카테고리 생성 거부. 인증 사용자 ID 없음");
      throw new BusinessException(AuthErrorCode.INVALID_ACCESS_TOKEN);
    }

    categoryCommandService.createCategory(command.userId(), command.categoryName());
  }

  /**
   * 인증된 사용자의 공부 카테고리 목록을 조회한다.
   *
   * @param userId 인증된 사용자 ID
   * @return 공부 카테고리 목록 조회 결과
   * @throws BusinessException 인증 정보가 없는 경우
   */
  @Transactional(readOnly = true)
  public CategoryListResult getCategories(String userId) {
    if (userId == null || userId.isBlank()) {
      LOG.debug("카테고리 목록 조회 거부. 인증 사용자 ID 없음");
      throw new BusinessException(AuthErrorCode.INVALID_ACCESS_TOKEN);
    }

    return categoryQueryService.getCategories(userId);
  }

  /**
   * 인증된 사용자의 공부 카테고리를 수정한다.
   *
   * @param command 카테고리 수정 입력 값
   * @throws BusinessException 인증 정보가 없거나, 입력값이 유효하지 않거나, 수정 권한이 없거나, 대상이 존재하지 않거나, 중복·동시 수정 충돌이 발생한
   *     경우
   */
  @Transactional
  public void updateCategory(UpdateCategoryCommand command) {
    if (command == null || command.userId() == null || command.userId().isBlank()) {
      LOG.debug("카테고리 수정 거부. 인증 사용자 ID 없음");
      throw new BusinessException(AuthErrorCode.INVALID_ACCESS_TOKEN);
    }

    categoryCommandService.updateCategory(
        command.userId(), command.categoryId(), command.categoryName());
  }

  /**
   * 인증된 사용자의 공부 카테고리를 삭제한다.
   *
   * @param userId 인증된 사용자 ID
   * @param categoryId 삭제할 카테고리 ID
   * @throws BusinessException 인증 정보가 없거나, 카테고리 ID가 유효하지 않거나, 삭제 권한이 없거나, 대상이 존재하지 않거나, 동시 삭제 충돌이
   *     발생한 경우
   */
  @Transactional
  public void deleteCategory(String userId, Long categoryId) {
    if (userId == null || userId.isBlank()) {
      LOG.debug("카테고리 삭제 거부. 인증 사용자 ID 없음");
      throw new BusinessException(AuthErrorCode.INVALID_ACCESS_TOKEN);
    }

    categoryCommandService.deleteCategory(userId, categoryId);
  }
}
