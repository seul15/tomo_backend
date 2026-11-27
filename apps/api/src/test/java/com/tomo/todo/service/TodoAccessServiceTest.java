package com.tomo.todo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.tomo.common.exception.BusinessException;
import com.tomo.common.exception.CommonErrorCode;
import com.tomo.todo.entity.Todo;
import com.tomo.todo.entity.TodoPriority;
import com.tomo.todo.exception.InvalidTodoIdException;
import com.tomo.todo.exception.TodoAccessDeniedException;
import com.tomo.todo.exception.TodoErrorCode;
import com.tomo.todo.exception.TodoNotFoundException;
import com.tomo.todo.repository.TodoRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TodoAccessServiceTest {
  @Mock private TodoRepository todoRepository;
  private TodoAccessService service;

  @BeforeEach
  void setUp() {
    service = new TodoAccessService(todoRepository);
  }

  @ParameterizedTest
  @EnumSource(Action.class)
  @DisplayName("본인의 삭제되지 않은 To-do는 각 작업의 대상으로 반환한다")
  void returnsOwnedTodo(Action action) {
    // given
    Todo todo = createTodo();
    when(todoRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(todo));
    // when
    Todo result = get(action, "owner", 1L);
    // then
    assertThat(result).isSameAs(todo);
  }

  @ParameterizedTest
  @EnumSource(Action.class)
  @DisplayName("다른 사용자 소유의 To-do는 작업별 전용 오류로 거부한다")
  void rejectsOtherOwnersTodo(Action action) {
    // given
    when(todoRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(createTodo()));
    // when
    // then
    assertThatThrownBy(() -> get(action, "another-user", 1L))
        .isInstanceOf(TodoAccessDeniedException.class)
        .extracting("errorCode")
        .isEqualTo(action.forbiddenCode);
  }

  @ParameterizedTest
  @EnumSource(Action.class)
  @DisplayName("삭제되지 않은 To-do 조회 결과가 없으면 전용 미존재 오류로 거부한다")
  void rejectsMissingTodo(Action action) {
    // given
    when(todoRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.empty());
    // when
    // then
    assertThatThrownBy(() -> get(action, "owner", 1L))
        .isInstanceOf(TodoNotFoundException.class)
        .extracting("errorCode")
        .isEqualTo(TodoErrorCode.TODO_NOT_FOUND);
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(longs = {0L, -1L})
  @DisplayName("누락 또는 양수가 아닌 To-do ID는 DB 조회 전에 전용 오류로 거부한다")
  void rejectsInvalidTodoId(Long todoId) {
    // when
    // then
    assertThatThrownBy(() -> service.getOwnedTodo("owner", todoId))
        .isInstanceOf(InvalidTodoIdException.class)
        .extracting("errorCode")
        .isEqualTo(TodoErrorCode.INVALID_TODO_ID);
    verifyNoInteractions(todoRepository);
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", " "})
  @DisplayName("누락 또는 공백 사용자 ID는 기존 공통 요청 오류로 거부한다")
  void rejectsInvalidUserId(String userId) {
    // when
    // then
    assertThatThrownBy(() -> service.getOwnedTodo(userId, 1L))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(CommonErrorCode.INVALID_REQUEST);
    verifyNoInteractions(todoRepository);
  }

  private Todo createTodo() {
    return Todo.create(
        "owner",
        "공부",
        "학습",
        TodoPriority.HIGH,
        LocalDate.of(2026, 11, 16),
        30,
        null,
        LocalDateTime.of(2026, 11, 16, 0, 0));
  }

  private Todo get(Action action, String userId, Long todoId) {
    return switch (action) {
      case VIEW -> service.getOwnedTodo(userId, todoId);
      case UPDATE -> service.getOwnedTodoForUpdate(userId, todoId);
      case DELETE -> service.getOwnedTodoForDelete(userId, todoId);
      case COMPLETION -> service.getOwnedTodoForCompletion(userId, todoId);
    };
  }

  private enum Action {
    VIEW(TodoErrorCode.TODO_VIEW_FORBIDDEN),
    UPDATE(TodoErrorCode.TODO_UPDATE_FORBIDDEN),
    DELETE(TodoErrorCode.TODO_DELETE_FORBIDDEN),
    COMPLETION(TodoErrorCode.TODO_COMPLETION_FORBIDDEN);

    private final TodoErrorCode forbiddenCode;

    Action(TodoErrorCode forbiddenCode) {
      this.forbiddenCode = forbiddenCode;
    }
  }
}
