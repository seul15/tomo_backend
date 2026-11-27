package com.tomo.category.controller;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tomo.category.exception.CategoryErrorCode;
import com.tomo.category.service.CategoryFacade;
import com.tomo.category.service.command.CreateCategoryCommand;
import com.tomo.category.service.command.UpdateCategoryCommand;
import com.tomo.category.service.model.CategoryItemResult;
import com.tomo.category.service.model.CategoryListResult;
import com.tomo.common.auth.config.SecurityConfig;
import com.tomo.common.auth.token.AccessTokenProvider;
import com.tomo.common.exception.BusinessException;
import com.tomo.common.exception.CommonErrorCode;
import com.tomo.user.service.LoginSessionService;
import io.jsonwebtoken.Jwts;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest(CategoryController.class)
@Import({SecurityConfig.class, CategoryControllerTest.JwtTestConfig.class})
@ActiveProfiles("test")
class CategoryControllerTest {
  private static final String PATH = "/api/v1/category";
  private static final String USER_ID = "user-id";
  private static final long SESSION_ID = 10L;
  private static final Instant NOW = Instant.parse("2026-11-26T00:00:00Z");

  @Autowired private MockMvc mockMvc;
  @Autowired private AccessTokenProvider accessTokenProvider;

  @MockitoBean private LoginSessionService loginSessionService;
  @MockitoBean private CategoryFacade categoryFacade;

  @Test
  @DisplayName("유효한 JWT와 카테고리 이름으로 요청하면 200과 data 없는 성공 응답을 반환한다")
  void createsCategory() throws Exception {
    // given
    String token = accessToken();
    when(loginSessionService.isValidAccessSession(USER_ID, SESSION_ID)).thenReturn(true);

    // when
    ResultActions result =
        mockMvc.perform(
            post(PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"category_nm\":\"전공\"}"));

    // then
    result
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").doesNotExist())
        .andExpect(jsonPath("$.meta.requestId").exists())
        .andExpect(jsonPath("$.meta.timestamp").isNotEmpty());

    verify(categoryFacade).createCategory(new CreateCategoryCommand(USER_ID, "전공"));
  }

  @Test
  @DisplayName("카테고리 생성 요청 본문이 없으면 400 INVALID_CATEGORY_NAME을 반환한다")
  void rejectsMissingCreateRequestBody() throws Exception {
    // given
    String token = accessToken();
    when(loginSessionService.isValidAccessSession(USER_ID, SESSION_ID)).thenReturn(true);

    // when
    ResultActions result =
        mockMvc.perform(
            post(PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON));

    // then
    result
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("INVALID_CATEGORY_NAME"));

    verifyNoInteractions(categoryFacade);
  }

  @Test
  @DisplayName("카테고리 이름이 유효하지 않으면 400 INVALID_CATEGORY_NAME을 반환한다")
  void rejectsInvalidCategoryName() throws Exception {
    // given
    String token = accessToken();
    CreateCategoryCommand command = new CreateCategoryCommand(USER_ID, "   ");
    when(loginSessionService.isValidAccessSession(USER_ID, SESSION_ID)).thenReturn(true);
    doThrow(new BusinessException(CategoryErrorCode.INVALID_CATEGORY_NAME))
        .when(categoryFacade)
        .createCategory(command);

    // when
    ResultActions result =
        mockMvc.perform(
            post(PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"category_nm\":\"   \"}"));

    // then
    result
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("INVALID_CATEGORY_NAME"));
  }

  @Test
  @DisplayName("Access Token이 없으면 401 INVALID_ACCESS_TOKEN을 반환한다")
  void rejectsMissingToken() throws Exception {
    // when
    ResultActions result =
        mockMvc.perform(
            post(PATH).contentType(MediaType.APPLICATION_JSON).content("{\"category_nm\":\"전공\"}"));

    // then
    result
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("INVALID_ACCESS_TOKEN"));

    verifyNoInteractions(categoryFacade);
  }

  @Test
  @DisplayName("생성 권한이 없으면 403 CATEGORY_CREATE_FORBIDDEN을 반환한다")
  void returnsCategoryCreateForbidden() throws Exception {
    // given
    String token = accessToken();
    CreateCategoryCommand command = new CreateCategoryCommand(USER_ID, "전공");
    when(loginSessionService.isValidAccessSession(USER_ID, SESSION_ID)).thenReturn(true);
    doThrow(new BusinessException(CategoryErrorCode.CATEGORY_CREATE_FORBIDDEN))
        .when(categoryFacade)
        .createCategory(command);

    // when
    ResultActions result =
        mockMvc.perform(
            post(PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"category_nm\":\"전공\"}"));

    // then
    result
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("CATEGORY_CREATE_FORBIDDEN"));
  }

  @Test
  @DisplayName("동일한 이름의 카테고리가 이미 존재하면 409 CATEGORY_ALREADY_EXISTS를 반환한다")
  void returnsCategoryAlreadyExists() throws Exception {
    // given
    String token = accessToken();
    CreateCategoryCommand command = new CreateCategoryCommand(USER_ID, "전공");
    when(loginSessionService.isValidAccessSession(USER_ID, SESSION_ID)).thenReturn(true);
    doThrow(new BusinessException(CategoryErrorCode.CATEGORY_ALREADY_EXISTS))
        .when(categoryFacade)
        .createCategory(command);

    // when
    ResultActions result =
        mockMvc.perform(
            post(PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"category_nm\":\"전공\"}"));

    // then
    result
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("CATEGORY_ALREADY_EXISTS"));
  }

  @Test
  @DisplayName("동시 요청 충돌이 발생하면 409 CONCURRENT_REQUEST_CONFLICT를 반환한다")
  void returnsConcurrentRequestConflict() throws Exception {
    // given
    String token = accessToken();
    CreateCategoryCommand command = new CreateCategoryCommand(USER_ID, "전공");
    when(loginSessionService.isValidAccessSession(USER_ID, SESSION_ID)).thenReturn(true);
    doThrow(new BusinessException(CategoryErrorCode.CONCURRENT_REQUEST_CONFLICT))
        .when(categoryFacade)
        .createCategory(command);

    // when
    ResultActions result =
        mockMvc.perform(
            post(PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"category_nm\":\"전공\"}"));

    // then
    result
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("CONCURRENT_REQUEST_CONFLICT"));
  }

  @Test
  @DisplayName("유효한 JWT로 카테고리 목록을 조회하면 200과 data.category 배열을 반환한다")
  void returnsCategoryList() throws Exception {
    // given
    String token = accessToken();
    when(loginSessionService.isValidAccessSession(USER_ID, SESSION_ID)).thenReturn(true);
    when(categoryFacade.getCategories(USER_ID))
        .thenReturn(
            new CategoryListResult(
                List.of(new CategoryItemResult(1L, "전공"), new CategoryItemResult(2L, "교양"))));

    // when
    ResultActions result =
        mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));

    // then
    result
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.category[0].categoryId").value(1))
        .andExpect(jsonPath("$.data.category[0].category_nm").value("전공"))
        .andExpect(jsonPath("$.data.category[1].categoryId").value(2))
        .andExpect(jsonPath("$.data.category[1].category_nm").value("교양"))
        .andExpect(jsonPath("$.meta.requestId").exists())
        .andExpect(jsonPath("$.meta.timestamp").isNotEmpty());

    verify(categoryFacade).getCategories(USER_ID);
  }

  @Test
  @DisplayName("카테고리 목록 조회 권한이 없으면 403 CATEGORY_VIEW_FORBIDDEN을 반환한다")
  void returnsCategoryViewForbidden() throws Exception {
    // given
    String token = accessToken();
    when(loginSessionService.isValidAccessSession(USER_ID, SESSION_ID)).thenReturn(true);
    when(categoryFacade.getCategories(USER_ID))
        .thenThrow(new BusinessException(CategoryErrorCode.CATEGORY_VIEW_FORBIDDEN));

    // when
    ResultActions result =
        mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));

    // then
    result
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("CATEGORY_VIEW_FORBIDDEN"));
  }

  @Test
  @DisplayName("유효한 JWT와 카테고리 수정 요청이면 200과 data 없는 성공 응답을 반환한다")
  void updatesCategory() throws Exception {
    // given
    String token = accessToken();
    when(loginSessionService.isValidAccessSession(USER_ID, SESSION_ID)).thenReturn(true);

    // when
    ResultActions result =
        mockMvc.perform(
            patch(PATH + "/1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"category_nm\":\"교양\"}"));

    // then
    result
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").doesNotExist())
        .andExpect(jsonPath("$.meta.requestId").exists())
        .andExpect(jsonPath("$.meta.timestamp").isNotEmpty());

    verify(categoryFacade).updateCategory(new UpdateCategoryCommand(USER_ID, 1L, "교양"));
  }

  @Test
  @DisplayName("카테고리 수정 요청 본문이 없으면 400 INVALID_REQUEST를 반환한다")
  void rejectsMissingUpdateCategoryRequestBody() throws Exception {
    // given
    String token = accessToken();
    when(loginSessionService.isValidAccessSession(USER_ID, SESSION_ID)).thenReturn(true);

    // when
    ResultActions result =
        mockMvc.perform(
            patch(PATH + "/1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON));

    // then
    result
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

    verifyNoInteractions(categoryFacade);
  }

  @Test
  @DisplayName("카테고리 수정 요청 필드가 유효하지 않으면 400 INVALID_REQUEST를 반환한다")
  void rejectsInvalidUpdateCategoryRequest() throws Exception {
    // given
    String token = accessToken();
    UpdateCategoryCommand command = new UpdateCategoryCommand(USER_ID, 1L, " 교양 ");
    when(loginSessionService.isValidAccessSession(USER_ID, SESSION_ID)).thenReturn(true);
    doThrow(new BusinessException(CommonErrorCode.INVALID_REQUEST))
        .when(categoryFacade)
        .updateCategory(command);

    // when
    ResultActions result =
        mockMvc.perform(
            patch(PATH + "/1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"category_nm\":\" 교양 \"}"));

    // then
    result
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
  }

  @Test
  @DisplayName("다른 사용자의 카테고리를 수정하면 403 CATEGORY_UPDATE_FORBIDDEN을 반환한다")
  void returnsCategoryUpdateForbidden() throws Exception {
    // given
    String token = accessToken();
    UpdateCategoryCommand command = new UpdateCategoryCommand(USER_ID, 1L, "교양");
    when(loginSessionService.isValidAccessSession(USER_ID, SESSION_ID)).thenReturn(true);
    doThrow(new BusinessException(CategoryErrorCode.CATEGORY_UPDATE_FORBIDDEN))
        .when(categoryFacade)
        .updateCategory(command);

    // when
    ResultActions result =
        mockMvc.perform(
            patch(PATH + "/1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"category_nm\":\"교양\"}"));

    // then
    result
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("CATEGORY_UPDATE_FORBIDDEN"));
  }

  @Test
  @DisplayName("존재하지 않는 카테고리를 수정하면 404 CATEGORY_NOT_FOUND를 반환한다")
  void returnsCategoryNotFoundWhenUpdating() throws Exception {
    // given
    String token = accessToken();
    UpdateCategoryCommand command = new UpdateCategoryCommand(USER_ID, 1L, "교양");
    when(loginSessionService.isValidAccessSession(USER_ID, SESSION_ID)).thenReturn(true);
    doThrow(new BusinessException(CategoryErrorCode.CATEGORY_NOT_FOUND))
        .when(categoryFacade)
        .updateCategory(command);

    // when
    ResultActions result =
        mockMvc.perform(
            patch(PATH + "/1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"category_nm\":\"교양\"}"));

    // then
    result
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("CATEGORY_NOT_FOUND"));
  }

  @Test
  @DisplayName("동시 수정 충돌이 발생하면 409 CATEGORY_UPDATE_CONFLICT를 반환한다")
  void returnsCategoryUpdateConflict() throws Exception {
    // given
    String token = accessToken();
    UpdateCategoryCommand command = new UpdateCategoryCommand(USER_ID, 1L, "교양");
    when(loginSessionService.isValidAccessSession(USER_ID, SESSION_ID)).thenReturn(true);
    doThrow(new BusinessException(CategoryErrorCode.CATEGORY_UPDATE_CONFLICT))
        .when(categoryFacade)
        .updateCategory(command);

    // when
    ResultActions result =
        mockMvc.perform(
            patch(PATH + "/1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"category_nm\":\"교양\"}"));

    // then
    result
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("CATEGORY_UPDATE_CONFLICT"));
  }

  @Test
  @DisplayName("유효한 JWT와 카테고리 ID로 삭제 요청하면 200과 data 없는 성공 응답을 반환한다")
  void deletesCategory() throws Exception {
    // given
    String token = accessToken();
    when(loginSessionService.isValidAccessSession(USER_ID, SESSION_ID)).thenReturn(true);

    // when
    ResultActions result =
        mockMvc.perform(delete(PATH + "/1").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));

    // then
    result
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").doesNotExist())
        .andExpect(jsonPath("$.meta.requestId").exists())
        .andExpect(jsonPath("$.meta.timestamp").isNotEmpty());

    verify(categoryFacade).deleteCategory(USER_ID, 1L);
  }

  @Test
  @DisplayName("카테고리 삭제 시 ID가 0 이하이면 400 INVALID_CATEGORY_ID를 반환한다")
  void rejectsNonPositiveCategoryIdWhenDeleting() throws Exception {
    // given
    String token = accessToken();
    when(loginSessionService.isValidAccessSession(USER_ID, SESSION_ID)).thenReturn(true);
    doThrow(new BusinessException(CategoryErrorCode.INVALID_CATEGORY_ID))
        .when(categoryFacade)
        .deleteCategory(USER_ID, 0L);

    // when
    ResultActions result =
        mockMvc.perform(delete(PATH + "/0").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));

    // then
    result
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_CATEGORY_ID"));
  }

  @Test
  @DisplayName("카테고리 삭제 시 ID가 숫자가 아니면 400 INVALID_CATEGORY_ID를 반환한다")
  void rejectsNonNumericCategoryIdWhenDeleting() throws Exception {
    // given
    String token = accessToken();
    when(loginSessionService.isValidAccessSession(USER_ID, SESSION_ID)).thenReturn(true);

    // when
    ResultActions result =
        mockMvc.perform(delete(PATH + "/전공").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));

    // then
    result
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_CATEGORY_ID"));

    verifyNoInteractions(categoryFacade);
  }

  @Test
  @DisplayName("다른 사용자의 카테고리를 삭제하면 403 CATEGORY_DELETE_FORBIDDEN을 반환한다")
  void returnsCategoryDeleteForbidden() throws Exception {
    // given
    String token = accessToken();
    when(loginSessionService.isValidAccessSession(USER_ID, SESSION_ID)).thenReturn(true);
    doThrow(new BusinessException(CategoryErrorCode.CATEGORY_DELETE_FORBIDDEN))
        .when(categoryFacade)
        .deleteCategory(USER_ID, 1L);

    // when
    ResultActions result =
        mockMvc.perform(delete(PATH + "/1").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));

    // then
    result
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("CATEGORY_DELETE_FORBIDDEN"));
  }

  @Test
  @DisplayName("존재하지 않는 카테고리를 삭제하면 404 CATEGORY_NOT_FOUND를 반환한다")
  void returnsCategoryNotFoundWhenDeleting() throws Exception {
    // given
    String token = accessToken();
    when(loginSessionService.isValidAccessSession(USER_ID, SESSION_ID)).thenReturn(true);
    doThrow(new BusinessException(CategoryErrorCode.CATEGORY_NOT_FOUND))
        .when(categoryFacade)
        .deleteCategory(USER_ID, 1L);

    // when
    ResultActions result =
        mockMvc.perform(delete(PATH + "/1").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));

    // then
    result
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("CATEGORY_NOT_FOUND"));
  }

  @Test
  @DisplayName("동시 삭제 충돌이 발생하면 409 CATEGORY_DELETE_CONFLICT를 반환한다")
  void returnsCategoryDeleteConflict() throws Exception {
    // given
    String token = accessToken();
    when(loginSessionService.isValidAccessSession(USER_ID, SESSION_ID)).thenReturn(true);
    doThrow(new BusinessException(CategoryErrorCode.CATEGORY_DELETE_CONFLICT))
        .when(categoryFacade)
        .deleteCategory(USER_ID, 1L);

    // when
    ResultActions result =
        mockMvc.perform(delete(PATH + "/1").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));

    // then
    result
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("CATEGORY_DELETE_CONFLICT"));
  }

  private String accessToken() {
    return accessTokenProvider.issue(USER_ID, SESSION_ID, NOW.plusSeconds(600));
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class JwtTestConfig {
    @Bean
    AccessTokenProvider accessTokenProvider() {
      return new AccessTokenProvider(
          Jwts.SIG.HS256.key().build(), Clock.fixed(NOW, ZoneOffset.UTC));
    }
  }
}
