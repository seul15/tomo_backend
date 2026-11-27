package com.tomo.todo.repository;

import com.tomo.todo.entity.Todo;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** To-do 저장과 조회를 제공한다. */
public interface TodoRepository extends JpaRepository<Todo, Long> {
  /**
   * 삭제되지 않은 To-do를 조회한다. 소유권은 호출 측에서 별도로 검증한다.
   *
   * @param id To-do 식별자
   * @return 삭제되지 않은 To-do
   */
  Optional<Todo> findByIdAndDeletedAtIsNull(Long id);

  /**
   * 사용자 소유의 삭제되지 않은 To-do를 조회한다.
   *
   * @param id To-do 식별자
   * @param userId 소유자 식별자
   * @return 조건에 맞는 To-do
   */
  Optional<Todo> findByIdAndUserIdAndDeletedAtIsNull(Long id, String userId);
}
