package com.tomo.todo.exception;

/** 삭제되지 않은 To-do를 찾을 수 없을 때 발생한다. */
public class TodoNotFoundException extends TodoException {
  private static final long serialVersionUID = 1L;

  /** 해당 검증 조건을 위반한 To-do 예외를 생성한다. */
  public TodoNotFoundException() {
    super(TodoErrorCode.TODO_NOT_FOUND);
  }
}
