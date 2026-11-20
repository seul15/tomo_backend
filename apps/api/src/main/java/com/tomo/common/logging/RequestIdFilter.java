package com.tomo.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/** 응답과 동일한 요청 식별자를 요청 처리 스레드의 로그에 연결한다. */
public class RequestIdFilter extends OncePerRequestFilter {

  /**
   * 비동기 재디스패치 스레드에도 요청 식별자를 등록하도록 필터를 실행한다.
   *
   * @return 비동기 디스패치를 건너뛰지 않으므로 {@code false}
   */
  @Override
  protected boolean shouldNotFilterAsyncDispatch() {
    return false;
  }

  /**
   * 오류 디스패치에서도 요청 식별자를 로그에 연결하도록 필터를 실행한다.
   *
   * @return 오류 디스패치를 건너뛰지 않으므로 {@code false}
   */
  @Override
  protected boolean shouldNotFilterErrorDispatch() {
    return false;
  }

  /**
   * 요청 식별자를 MDC에 등록하고 후속 처리 종료 시 기존 컨텍스트를 복원한다.
   *
   * <p>기존 요청 식별자가 없으면 MDC에서 제거하며, 후속 처리에서 예외가 발생해도 동일하게 정리한다.
   *
   * @param request 요청 식별자를 제공하는 HTTP 요청
   * @param response HTTP 응답
   * @param filterChain 후속 필터 체인
   * @throws ServletException 후속 필터 처리 중 서블릿 오류가 발생한 경우
   * @throws IOException 후속 필터 처리 중 입출력 오류가 발생한 경우
   */
  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String previousRequestId = MDC.get("requestId");
    MDC.put("requestId", request.getRequestId());
    try {
      filterChain.doFilter(request, response);
    } finally {
      if (previousRequestId == null) {
        MDC.remove("requestId");
      } else {
        MDC.put("requestId", previousRequestId);
      }
    }
  }
}
