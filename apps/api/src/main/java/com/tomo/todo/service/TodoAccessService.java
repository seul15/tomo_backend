package com.tomo.todo.service;

import com.tomo.common.exception.BusinessException;
import com.tomo.common.exception.CommonErrorCode;
import com.tomo.todo.entity.Todo;
import com.tomo.todo.exception.InvalidTodoIdException;
import com.tomo.todo.exception.TodoAccessDeniedException;
import com.tomo.todo.exception.TodoErrorCode;
import com.tomo.todo.exception.TodoNotFoundException;
import com.tomo.todo.repository.TodoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** To-do 소유권을 검증하고 삭제되지 않은 대상을 조회한다. */
@Service
@Transactional(readOnly = true)
public class TodoAccessService {
  private final TodoRepository todoRepository;

  /**
   * To-do 조회에 필요한 저장소를 주입한다.
   *
   * @param todoRepository To-do 저장소
   */
  public TodoAccessService(TodoRepository todoRepository) {
    this.todoRepository = todoRepository;
  }

  /**
   * 조회 대상인 삭제되지 않은 To-do의 소유권을 확인한다.
   *
   * @param userId 인증 계층에서 전달한 사용자 식별자
   * @param todoId To-do 식별자
   * @return 사용자 소유의 삭제되지 않은 To-do
   * @throws BusinessException 사용자 식별자가 유효하지 않은 경우
   * @throws InvalidTodoIdException To-do 식별자가 누락되었거나 양수가 아닌 경우
   * @throws TodoNotFoundException 대상이 없거나 삭제된 경우
   * @throws TodoAccessDeniedException 대상의 소유자가 아닌 경우
   */
  public Todo getOwnedTodo(String userId, Long todoId) {
    return getOwnedTodo(userId, todoId, TodoErrorCode.TODO_VIEW_FORBIDDEN);
  }

  /**
   * 수정 대상인 삭제되지 않은 To-do의 소유권을 확인한다.
   *
   * @param userId 인증 계층에서 전달한 사용자 식별자
   * @param todoId To-do 식별자
   * @return 사용자 소유의 삭제되지 않은 To-do
   * @throws BusinessException 사용자 식별자가 유효하지 않은 경우
   * @throws InvalidTodoIdException To-do 식별자가 누락되었거나 양수가 아닌 경우
   * @throws TodoNotFoundException 대상이 없거나 삭제된 경우
   * @throws TodoAccessDeniedException 대상의 소유자가 아닌 경우
   */
  public Todo getOwnedTodoForUpdate(String userId, Long todoId) {
    return getOwnedTodo(userId, todoId, TodoErrorCode.TODO_UPDATE_FORBIDDEN);
  }

  /**
   * 삭제 대상인 삭제되지 않은 To-do의 소유권을 확인한다.
   *
   * @param userId 인증 계층에서 전달한 사용자 식별자
   * @param todoId To-do 식별자
   * @return 사용자 소유의 삭제되지 않은 To-do
   * @throws BusinessException 사용자 식별자가 유효하지 않은 경우
   * @throws InvalidTodoIdException To-do 식별자가 누락되었거나 양수가 아닌 경우
   * @throws TodoNotFoundException 대상이 없거나 삭제된 경우
   * @throws TodoAccessDeniedException 대상의 소유자가 아닌 경우
   */
  public Todo getOwnedTodoForDelete(String userId, Long todoId) {
    return getOwnedTodo(userId, todoId, TodoErrorCode.TODO_DELETE_FORBIDDEN);
  }

  /**
   * 완료 상태 변경 대상인 삭제되지 않은 To-do의 소유권을 확인한다.
   *
   * @param userId 인증 계층에서 전달한 사용자 식별자
   * @param todoId To-do 식별자
   * @return 사용자 소유의 삭제되지 않은 To-do
   * @throws BusinessException 사용자 식별자가 유효하지 않은 경우
   * @throws InvalidTodoIdException To-do 식별자가 누락되었거나 양수가 아닌 경우
   * @throws TodoNotFoundException 대상이 없거나 삭제된 경우
   * @throws TodoAccessDeniedException 대상의 소유자가 아닌 경우
   */
  public Todo getOwnedTodoForCompletion(String userId, Long todoId) {
    return getOwnedTodo(userId, todoId, TodoErrorCode.TODO_COMPLETION_FORBIDDEN);
  }

  private Todo getOwnedTodo(String userId, Long todoId, TodoErrorCode forbiddenCode) {
    if (userId == null || userId.isBlank()) {
      throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
    }
    if (todoId == null || todoId <= 0) {
      throw new InvalidTodoIdException();
    }

    Todo todo =
        todoRepository.findByIdAndDeletedAtIsNull(todoId).orElseThrow(TodoNotFoundException::new);
    if (!userId.equals(todo.getUserId())) {
      throw new TodoAccessDeniedException(forbiddenCode);
    }
    return todo;
  }
}
