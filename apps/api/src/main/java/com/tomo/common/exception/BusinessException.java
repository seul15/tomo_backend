package com.tomo.common.exception;

/** 비즈니스 규칙 위반을 나타내는 공통 예외입니다. */
public class BusinessException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  private final ErrorCode errorCode;

  /**
   * 에러코드로 비즈니스 예외를 생성합니다.
   *
   * @param errorCode 발생한 오류 코드
   */
  public BusinessException(ErrorCode errorCode) {
    super(errorCode.getMessage());
    this.errorCode = errorCode;
  }

  public ErrorCode getErrorCode() {
    return errorCode;
  }
}
