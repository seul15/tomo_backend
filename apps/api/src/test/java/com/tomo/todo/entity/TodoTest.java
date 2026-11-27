package com.tomo.todo.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tomo.todo.exception.InvalidTodoEstimateException;
import com.tomo.todo.exception.TodoErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TodoTest {
  @Test
  @DisplayName("신규 To-do는 미완료·미삭제 상태이며 알림 없이 생성할 수 있다")
  void createsIncompleteTodoWithoutReminder() {
    // given
    LocalDateTime now = LocalDateTime.of(2026, 11, 13, 12, 0);
    // when
    Todo todo =
        Todo.create(
            "owner", "자료구조", "전공", TodoPriority.HIGH, LocalDate.of(2026, 11, 14), 60, null, now);
    // then
    assertThat(todo.getCompletedAt()).isNull();
    assertThat(todo.getDeletedAt()).isNull();
    assertThat(todo.getReminderAt()).isNull();
    assertThat(todo.getCreatedAt()).isEqualTo(now);
    assertThat(todo.getUpdatedAt()).isEqualTo(now);
  }

  @ParameterizedTest
  @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
  @DisplayName("양수가 아닌 예상 학습시간은 생성 시 거부한다")
  void rejectsNonpositiveEstimate(int estimatedMinutes) {
    // when
    // then
    assertThatThrownBy(
            () ->
                Todo.create(
                    "owner",
                    "자료구조",
                    "전공",
                    TodoPriority.HIGH,
                    LocalDate.of(2026, 11, 14),
                    estimatedMinutes,
                    null,
                    LocalDateTime.of(2026, 11, 13, 12, 0)))
        .isInstanceOf(InvalidTodoEstimateException.class)
        .extracting("errorCode")
        .isEqualTo(TodoErrorCode.INVALID_TODO_ESTIMATE);
  }

  @Test
  @DisplayName("예상 학습시간의 최소 양수인 1분으로 생성할 수 있다")
  void acceptsMinimumPositiveEstimate() {
    // when
    Todo todo =
        Todo.create(
            "owner",
            "자료구조",
            "전공",
            TodoPriority.HIGH,
            LocalDate.of(2026, 11, 14),
            1,
            null,
            LocalDateTime.of(2026, 11, 13, 12, 0));
    // then
    assertThat(todo.getEstimatedMinutes()).isEqualTo(1);
  }
}
