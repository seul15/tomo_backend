package com.tomo.common.exception;

import com.tomo.common.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** API 예외를 공통 오류 응답으로 변환한다. */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
  private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  /**
   * 업무 예외를 처리한다.
   *
   * @param exception 발생한 업무 예외
   * @param request HTTP 요청
   * @return 공통 오류 응답
   */
  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ErrorResponse> handleBusinessException(
      BusinessException exception, HttpServletRequest request) {

    ErrorCode errorCode = exception.getErrorCode();

    return ResponseEntity.status(errorCode.getHttpStatus())
        .body(ErrorResponse.from(errorCode, request.getRequestId()));
  }

  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception exception,
      Object body,
      HttpHeaders headers,
      HttpStatusCode statusCode,
      WebRequest request) {

    ErrorCode errorCode =
        switch (statusCode.value()) {
          case 400 -> ErrorCode.INVALID_REQUEST;
          case 401 -> ErrorCode.UNAUTHORIZED;
          case 404 -> ErrorCode.RESOURCE_NOT_FOUND;
          case 409 -> ErrorCode.RESOURCE_CONFLICT;
          case 500 -> ErrorCode.INTERNAL_SERVER_ERROR;
          default -> null;
        };

    if (errorCode == null) {
      return super.handleExceptionInternal(exception, body, headers, statusCode, request);
    }

    String requestId = ((ServletWebRequest) request).getRequest().getRequestId();

    if (statusCode.is5xxServerError()) {
      LOG.error("서버 오류 발생. requestId={}", requestId, exception);
    }

    return super.handleExceptionInternal(
        exception, ErrorResponse.from(errorCode, requestId), headers, statusCode, request);
  }

  /**
   * 예상하지 못한 서버 오류를 처리한다.
   *
   * @param exception 발생한 예외
   * @param request HTTP 요청
   * @return 내부 서버 오류 응답
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleUnexpectedException(
      Exception exception, HttpServletRequest request) {

    ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;
    String requestId = request.getRequestId();

    LOG.error("서버 오류 발생. requestId={}", requestId, exception);

    return ResponseEntity.status(errorCode.getHttpStatus())
        .body(ErrorResponse.from(errorCode, requestId));
  }
}
