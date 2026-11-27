package com.tomo.todo.exception;

/** To-do 식별자가 누락되었거나 양수가 아닐 때 발생한다. */
public class InvalidTodoIdException extends TodoException {
  private static final long serialVersionUID = 1L;

  /** 해당 검증 조건을 위반한 To-do 예외를 생성한다. */
  public InvalidTodoIdException() {
    super(TodoErrorCode.INVALID_TODO_ID);
  }
}
