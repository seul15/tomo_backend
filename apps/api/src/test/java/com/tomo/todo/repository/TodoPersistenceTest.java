package com.tomo.todo.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.tomo.todo.entity.Todo;
import com.tomo.todo.entity.TodoPriority;
import com.tomo.user.entity.User;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TodoPersistenceTest {
  @Autowired private EntityManager entityManager;
  @Autowired private TodoRepository todoRepository;

  @Test
  @DisplayName("Flyway 스키마에 To-do를 저장하고 소유자 조건으로 조회한다")
  void savesAndFindsOwnedTodo() {
    // given
    User user = User.createPending();
    entityManager.persist(user);
    entityManager.flush();
    LocalDateTime now = LocalDateTime.of(2026, 11, 13, 12, 0);
    Todo saved =
        todoRepository.save(
            Todo.create(
                user.getId(),
                "자료구조",
                "전공",
                TodoPriority.HIGH,
                LocalDate.of(2026, 11, 14),
                60,
                now.plusHours(1),
                now));
    entityManager.flush();
    entityManager.clear();
    // when
    Todo found =
        todoRepository
            .findByIdAndUserIdAndDeletedAtIsNull(saved.getId(), user.getId())
            .orElseThrow();
    // then
    assertThat(found.getTitle()).isEqualTo("자료구조");
    assertThat(found.getCategoryName()).isEqualTo("전공");
    assertThat(found.getPriority()).isEqualTo(TodoPriority.HIGH);
    assertThat(found.getReminderAt()).isEqualTo(now.plusHours(1));
    assertThat(found.getDueDate()).isEqualTo(LocalDate.of(2026, 11, 14));
    assertThat(found.getEstimatedMinutes()).isEqualTo(60);
    assertThat(todoRepository.findByIdAndUserIdAndDeletedAtIsNull(saved.getId(), "other"))
        .isEmpty();
    assertThat(todoRepository.findByIdAndDeletedAtIsNull(Long.MAX_VALUE)).isEmpty();
  }

  @Test
  @DisplayName("삭제된 To-do는 일반 조회에서 제외하고 원본 행은 보존한다")
  void excludesDeletedTodoWithoutRemovingHistory() {
    // given
    User user = User.createPending();
    entityManager.persist(user);
    entityManager.flush();
    LocalDateTime now = LocalDateTime.of(2026, 11, 13, 12, 0);
    Todo saved =
        todoRepository.saveAndFlush(
            Todo.create(
                user.getId(),
                "자료구조",
                "전공",
                TodoPriority.LOW,
                LocalDate.of(2026, 11, 14),
                30,
                null,
                now));
    entityManager
        .createNativeQuery("UPDATE todos SET deleted_at = :now WHERE todo_id = :id")
        .setParameter("now", now)
        .setParameter("id", saved.getId())
        .executeUpdate();
    entityManager.clear();
    // then
    assertThat(todoRepository.findByIdAndDeletedAtIsNull(saved.getId())).isEmpty();
    assertThat(todoRepository.findByIdAndUserIdAndDeletedAtIsNull(saved.getId(), user.getId()))
        .isEmpty();
    Todo retained = entityManager.find(Todo.class, saved.getId());
    assertThat(retained).isNotNull();
    assertThat(retained.getTitle()).isEqualTo("자료구조");
    assertThat(retained.getDeletedAt()).isEqualTo(now);
  }
}
