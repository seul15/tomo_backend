package com.tomo.todo.exception;

/** To-do 작업을 수행할 소유권이 없을 때 발생한다. */
public class TodoAccessDeniedException extends TodoException {
  private static final long serialVersionUID = 1L;

  /**
   * 해당 작업의 권한 오류로 예외를 생성한다.
   *
   * @param errorCode 작업별 TODO_*_FORBIDDEN 오류 코드
   */
  public TodoAccessDeniedException(TodoErrorCode errorCode) {
    super(errorCode);
  }
}
