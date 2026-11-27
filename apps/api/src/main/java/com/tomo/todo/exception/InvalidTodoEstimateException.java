package com.tomo.todo.exception;

/** 예상 학습시간이 양수가 아닐 때 발생한다. */
public class InvalidTodoEstimateException extends TodoException {
  private static final long serialVersionUID = 1L;

  /** 해당 검증 조건을 위반한 To-do 예외를 생성한다. */
  public InvalidTodoEstimateException() {
    super(TodoErrorCode.INVALID_TODO_ESTIMATE);
  }
}
