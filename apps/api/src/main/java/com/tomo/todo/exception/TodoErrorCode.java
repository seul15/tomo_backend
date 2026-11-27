package com.tomo.todo.exception;

import com.tomo.common.exception.ErrorCode;

/** API 명세에 정의된 To-do 기능의 오류 코드다. */
public enum TodoErrorCode implements ErrorCode {
  /** todoId가 올바르지 않거나 유효한 ID 형식이 아닙니다. */
  INVALID_TODO_ID(400, "todoId가 올바르지 않거나 유효한 ID 형식이 아닙니다."),

  /** 예상 학습시간이 양수가 아닌 경우입니다. */
  INVALID_TODO_ESTIMATE(400, "예상 학습시간은 1분 이상이어야 합니다."),

  /** To-do를 생성할 권한이 없습니다. */
  TODO_CREATE_FORBIDDEN(403, "To-do를 생성할 권한이 없습니다."),

  /** 다른 사용자가 등록한 To-do를 조회할 권한이 없습니다. */
  TODO_VIEW_FORBIDDEN(403, "다른 사용자가 등록한 To-do를 조회할 권한이 없습니다."),

  /** 다른 사용자가 등록한 To-do를 수정할 권한이 없습니다. */
  TODO_UPDATE_FORBIDDEN(403, "다른 사용자가 등록한 To-do를 수정할 권한이 없습니다."),

  /** 다른 사용자가 등록한 To-do를 삭제할 권한이 없습니다. */
  TODO_DELETE_FORBIDDEN(403, "다른 사용자가 등록한 To-do를 삭제할 권한이 없습니다."),

  /** 다른 사용자가 등록한 To-do의 완료 상태를 변경할 권한이 없습니다. */
  TODO_COMPLETION_FORBIDDEN(403, "다른 사용자가 등록한 To-do의 완료 상태를 변경할 권한이 없습니다."),

  /** 요청한 To-do가 존재하지 않습니다. */
  TODO_NOT_FOUND(404, "요청한 To-do가 존재하지 않습니다."),

  /** 동일한 To-do에 대한 동시 수정 요청으로 충돌이 발생했습니다. */
  TODO_UPDATE_CONFLICT(409, "동일한 To-do에 대한 동시 수정 요청으로 충돌이 발생했습니다."),

  /** 동일한 To-do에 대한 동시 삭제 요청으로 충돌이 발생했습니다. */
  TODO_DELETE_CONFLICT(409, "동일한 To-do에 대한 동시 삭제 요청으로 충돌이 발생했습니다."),

  /** To-do의 현재 상태와 요청한 완료 상태가 충돌합니다. */
  TODO_STATE_CONFLICT(409, "To-do의 현재 상태와 요청한 완료 상태가 충돌합니다.");

  private final int httpStatus;
  private final String message;

  TodoErrorCode(int httpStatus, String message) {
    this.httpStatus = httpStatus;
    this.message = message;
  }

  /**
   * 클라이언트에 전달할 오류 코드를 반환한다.
   *
   * @return 오류 코드
   */
  @Override
  public String code() {
    return name();
  }

  /**
   * 클라이언트에 전달할 오류 메시지를 반환한다.
   *
   * @return 오류 메시지
   */
  @Override
  public String message() {
    return message;
  }

  /**
   * 오류의 HTTP 상태 코드를 반환한다.
   *
   * @return HTTP 상태 코드
   */
  @Override
  public int httpStatus() {
    return httpStatus;
  }
}
