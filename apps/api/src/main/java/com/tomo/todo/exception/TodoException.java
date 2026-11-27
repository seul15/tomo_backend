package com.tomo.todo.exception;

import com.tomo.common.exception.BusinessException;
import com.tomo.common.exception.ErrorCode;

/** To-do 업무 규칙 위반을 나타내는 예외의 공통 골격이다. */
public abstract class TodoException extends BusinessException {
  private static final long serialVersionUID = 1L;

  /**
   * 오류 코드로 To-do 업무 예외를 생성한다.
   *
   * @param errorCode 발생한 오류 코드
   */
  protected TodoException(ErrorCode errorCode) {
    super(errorCode);
  }
}
