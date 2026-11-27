package com.tomo.todo.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tomo.common.exception.BusinessException;
import com.tomo.common.exception.GlobalExceptionHandler;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

class TodoExceptionHandlerTest {
  private final ObjectMapper objectMapper = new ObjectMapper();
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new ErrorController())
            .defaultRequest(get("/").accept(MediaType.APPLICATION_JSON))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @ParameterizedTest
  @MethodSource("errorContracts")
  @DisplayName("To-do 오류 코드는 명세의 응답 코드와 HTTP 상태를 제공한다")
  void matchesErrorContract(TodoErrorCode code, String expectedCode, int expectedStatus) {
    // then
    assertThat(code.code()).isEqualTo(expectedCode);
    assertThat(code.httpStatus()).isEqualTo(expectedStatus);
    assertThat(code.message()).isNotBlank();
  }

  @ParameterizedTest
  @EnumSource(TodoErrorCode.class)
  @DisplayName("전역 핸들러는 To-do 오류를 기존 공통 오류 응답으로 변환한다")
  void returnsTodoErrorResponse(TodoErrorCode code) throws Exception {
    // when
    MockHttpServletResponse response =
        mockMvc
            .perform(get("/test/todo-errors").param("code", code.name()))
            .andReturn()
            .getResponse();
    // then
    assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
    JsonNode body = objectMapper.readTree(response.getContentAsByteArray());
    assertThat(response.getStatus()).isEqualTo(code.httpStatus());
    assertThat(body.path("success").asBoolean()).isFalse();
    assertThat(body.path("error").path("code").asText()).isEqualTo(code.code());
    assertThat(body.path("error").path("message").asText()).isEqualTo(code.message());
    assertThat(body.path("meta").has("requestId")).isTrue();
    assertThat(body.path("meta").path("timestamp").asText()).isNotBlank();
  }

  @Test
  @DisplayName("To-do 하위 예외도 기존 BusinessException 핸들러가 처리한다")
  void handlesTodoExceptionSubclass() throws Exception {
    // when
    MockHttpServletResponse response =
        mockMvc.perform(get("/test/todo-missing")).andReturn().getResponse();
    // then
    assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
    assertThat(response.getStatus()).isEqualTo(404);
    assertThat(
            objectMapper
                .readTree(response.getContentAsByteArray())
                .path("error")
                .path("code")
                .asText())
        .isEqualTo("TODO_NOT_FOUND");
  }

  @Test
  @DisplayName("예상 시간 도메인 검증 실패는 서버 오류 대신 400으로 반환한다")
  void handlesInvalidEstimate() throws Exception {
    // when
    MockHttpServletResponse response =
        mockMvc.perform(get("/test/todo-estimate")).andReturn().getResponse();
    // then
    assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
    assertThat(response.getStatus()).isEqualTo(400);
    assertThat(
            objectMapper
                .readTree(response.getContentAsByteArray())
                .path("error")
                .path("code")
                .asText())
        .isEqualTo(TodoErrorCode.INVALID_TODO_ESTIMATE.code());
  }

  private static Stream<Arguments> errorContracts() {
    return Stream.of(
        Arguments.of(TodoErrorCode.INVALID_TODO_ID, "INVALID_TODO_ID", 400),
        Arguments.of(TodoErrorCode.TODO_CREATE_FORBIDDEN, "TODO_CREATE_FORBIDDEN", 403),
        Arguments.of(TodoErrorCode.TODO_VIEW_FORBIDDEN, "TODO_VIEW_FORBIDDEN", 403),
        Arguments.of(TodoErrorCode.TODO_UPDATE_FORBIDDEN, "TODO_UPDATE_FORBIDDEN", 403),
        Arguments.of(TodoErrorCode.TODO_DELETE_FORBIDDEN, "TODO_DELETE_FORBIDDEN", 403),
        Arguments.of(TodoErrorCode.TODO_COMPLETION_FORBIDDEN, "TODO_COMPLETION_FORBIDDEN", 403),
        Arguments.of(TodoErrorCode.TODO_NOT_FOUND, "TODO_NOT_FOUND", 404),
        Arguments.of(TodoErrorCode.TODO_UPDATE_CONFLICT, "TODO_UPDATE_CONFLICT", 409),
        Arguments.of(TodoErrorCode.TODO_DELETE_CONFLICT, "TODO_DELETE_CONFLICT", 409),
        Arguments.of(TodoErrorCode.INVALID_TODO_ESTIMATE, "INVALID_TODO_ESTIMATE", 400),
        Arguments.of(TodoErrorCode.TODO_STATE_CONFLICT, "TODO_STATE_CONFLICT", 409));
  }

  @RestController
  static class ErrorController {
    @GetMapping("/test/todo-errors")
    public void error(@RequestParam("code") TodoErrorCode code) {
      throw new BusinessException(code);
    }

    @GetMapping("/test/todo-missing")
    public void missing() {
      throw new TodoNotFoundException();
    }

    @GetMapping("/test/todo-estimate")
    public void estimate() {
      throw new InvalidTodoEstimateException();
    }
  }
}
