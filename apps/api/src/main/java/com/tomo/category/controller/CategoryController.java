package com.tomo.category.controller;

import com.tomo.category.controller.docs.CategoryApiDocs;
import com.tomo.category.dto.request.CategoryCreateRequest;
import com.tomo.category.dto.request.CategoryUpdateRequest;
import com.tomo.category.dto.response.CategoryListResponse;
import com.tomo.category.exception.CategoryErrorCode;
import com.tomo.category.service.CategoryFacade;
import com.tomo.common.exception.BusinessException;
import com.tomo.common.exception.CommonErrorCode;
import com.tomo.common.response.CommonResponse;
import com.tomo.common.response.ErrorResponse;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** 공부 카테고리 기능을 처리한다. */
@RestController
@RequestMapping("/api/v1/category")
public class CategoryController implements CategoryApiDocs {
  private static final Logger LOG = LoggerFactory.getLogger(CategoryController.class);

  private final CategoryFacade categoryFacade;

  /**
   * 카테고리 Facade를 주입받는다.
   *
   * @param categoryFacade 카테고리 업무 조정 객체
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Spring이 관리하는 Facade 빈을 공유 의존성으로 주입받는다.")
  public CategoryController(CategoryFacade categoryFacade) {
    this.categoryFacade = categoryFacade;
  }

  /**
   * 현재 인증된 사용자의 공부 카테고리를 생성한다.
   *
   * @param userId 인증된 사용자 ID
   * @param requestBody 카테고리 생성 요청 본문
   * @param request HTTP 요청
   * @return 데이터가 없는 성공 응답
   * @throws BusinessException 요청 본문이나 카테고리 이름이 유효하지 않거나 생성할 수 없는 경우
   */
  @Override
  @PostMapping
  public CommonResponse<Void> createCategory(
      @AuthenticationPrincipal String userId,
      @RequestBody(required = false) CategoryCreateRequest requestBody,
      HttpServletRequest request) {
    if (requestBody == null) {
      throw new BusinessException(CategoryErrorCode.INVALID_CATEGORY_NAME);
    }

    categoryFacade.createCategory(requestBody.toCommand(userId));

    LOG.debug("카테고리 생성 완료");

    return CommonResponse.successWithoutData(request.getRequestId());
  }

  /**
   * 현재 인증된 사용자의 공부 카테고리 목록을 조회한다.
   *
   * @param userId 인증된 사용자 ID
   * @param request HTTP 요청
   * @return 공부 카테고리 목록 응답
   */
  @Override
  @GetMapping
  public CommonResponse<CategoryListResponse> getCategories(
      @AuthenticationPrincipal String userId, HttpServletRequest request) {
    CategoryListResponse response = CategoryListResponse.from(categoryFacade.getCategories(userId));

    LOG.debug("카테고리 목록 조회 완료");

    return CommonResponse.success(response, request.getRequestId());
  }

  /**
   * 현재 인증된 사용자의 공부 카테고리를 수정한다.
   *
   * @param userId 인증된 사용자 ID
   * @param categoryId 수정할 카테고리 ID
   * @param requestBody 카테고리 수정 요청 본문
   * @param request HTTP 요청
   * @return 데이터가 없는 성공 응답
   * @throws BusinessException 요청 값이 유효하지 않거나 수정할 수 없는 경우
   */
  @Override
  @PatchMapping("/{categoryId}")
  public CommonResponse<Void> updateCategory(
      @AuthenticationPrincipal String userId,
      @PathVariable("categoryId") Long categoryId,
      @RequestBody(required = false) CategoryUpdateRequest requestBody,
      HttpServletRequest request) {
    if (requestBody == null) {
      throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
    }

    categoryFacade.updateCategory(requestBody.toCommand(userId, categoryId));

    LOG.debug("카테고리 수정 완료");

    return CommonResponse.successWithoutData(request.getRequestId());
  }

  /**
   * 현재 인증된 사용자의 공부 카테고리를 삭제한다.
   *
   * @param userId 인증된 사용자 ID
   * @param categoryId 삭제할 카테고리 ID
   * @param request HTTP 요청
   * @return 데이터가 없는 성공 응답
   * @throws BusinessException 카테고리 ID가 유효하지 않거나 삭제할 수 없는 경우
   */
  @Override
  @DeleteMapping("/{categoryId}")
  public CommonResponse<Void> deleteCategory(
      @AuthenticationPrincipal String userId,
      @PathVariable("categoryId") Long categoryId,
      HttpServletRequest request) {
    categoryFacade.deleteCategory(userId, categoryId);

    LOG.debug("카테고리 삭제 완료");

    return CommonResponse.successWithoutData(request.getRequestId());
  }

  /**
   * 카테고리 삭제 요청에서 categoryId 경로 변수 타입 변환 실패 시 INVALID_CATEGORY_ID 오류 응답을 반환한다.
   *
   * @param exception 경로 변수 타입 불일치 예외
   * @param request HTTP 요청
   * @return 오류 응답
   */
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ErrorResponse> handleCategoryIdTypeMismatch(
      MethodArgumentTypeMismatchException exception, HttpServletRequest request) {
    if ("DELETE".equalsIgnoreCase(request.getMethod())
        && "categoryId".equals(exception.getName())) {
      LOG.debug("카테고리 삭제 ID 경로 변수 타입 불일치");
      return ResponseEntity.status(CategoryErrorCode.INVALID_CATEGORY_ID.httpStatus())
          .body(ErrorResponse.from(CategoryErrorCode.INVALID_CATEGORY_ID, request.getRequestId()));
    }
    LOG.debug("카테고리 요청 경로 변수 타입 불일치");
    return ResponseEntity.status(CommonErrorCode.INVALID_REQUEST.httpStatus())
        .body(ErrorResponse.from(CommonErrorCode.INVALID_REQUEST, request.getRequestId()));
  }
}
