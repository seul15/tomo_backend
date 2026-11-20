package com.tomo.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.status.Status;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.logging.LoggingInitializationContext;
import org.springframework.mock.env.MockEnvironment;

class LogbackConfigurationTest {

  @ParameterizedTest
  @ValueSource(strings = {"local", "dev"})
  @DisplayName("실제 프로필 XML은 로그 메시지를 유지하고 메시지와 예외 체인의 비밀값을 마스킹한다")
  void masksMessagesAndExceptionsFromXml(String profile) throws Exception {
    // given
    MockEnvironment environment = new MockEnvironment();
    environment.setActiveProfiles(profile);
    Constructor<?> constructor =
        Class.forName("org.springframework.boot.logging.logback.SpringBootJoranConfigurator")
            .getDeclaredConstructor(LoggingInitializationContext.class);
    constructor.setAccessible(true);
    LoggerContext context = new LoggerContext();
    try {
      JoranConfigurator configurator =
          (JoranConfigurator)
              constructor.newInstance(new LoggingInitializationContext(environment));
      configurator.setContext(context);
      configurator.doConfigure(getClass().getResource("/logback-spring.xml"));
      Logger logger = context.getLogger("com.tomo.logging.test");
      LoggingEvent event =
          new LoggingEvent(
              getClass().getName(),
              logger,
              Level.ERROR,
              "Request {} failed password=message-secret Bearer abc/def+ghi~jkl==",
              null,
              new Object[] {42});
      IllegalStateException exception =
          new IllegalStateException(
              "password=exception-secret", new IllegalArgumentException("apiKey=cause-secret"));
      exception.addSuppressed(new IllegalStateException("token=suppressed-secret"));
      event.setThrowableProxy(new ThrowableProxy(exception));
      event.setMDCPropertyMap(Map.of("requestId", "request-log-test"));
      ConsoleAppender<?> appender =
          (ConsoleAppender<?>) context.getLogger(Logger.ROOT_LOGGER_NAME).getAppender("CONSOLE");

      // when
      @SuppressWarnings("unchecked")
      byte[] encoded =
          ((ConsoleAppender<ch.qos.logback.classic.spi.ILoggingEvent>) appender)
              .getEncoder()
              .encode(event);
      String output = new String(encoded, StandardCharsets.UTF_8);

      // then
      assertThat(context.getStatusManager().getCopyOfStatusList())
          .noneMatch(status -> status.getLevel() == Status.ERROR);
      assertThat(output)
          .contains("[requestId=request-log-test]")
          .contains("Request 42 failed password=****** Bearer ******")
          .contains("IllegalStateException: password=******")
          .contains("IllegalArgumentException: apiKey=******")
          .contains("token=******")
          .contains("at com.tomo.common.logging.LogbackConfigurationTest")
          .doesNotContain("message-secret", "exception-secret", "cause-secret", "suppressed-secret")
          .doesNotContain("abc/def+ghi~jkl==", "%PARSER_ERROR", "%n", "%nopex");
      assertThat(output.split("IllegalStateException: password=", -1)).hasSize(2);
      LoggingEvent normalEvent =
          new LoggingEvent(
              getClass().getName(),
              logger,
              Level.INFO,
              "Request {} failed password=message-secret Bearer abc/def+ghi~jkl==",
              null,
              new Object[] {42});
      normalEvent.setMDCPropertyMap(Map.of());
      @SuppressWarnings("unchecked")
      String withoutException =
          new String(
              ((ConsoleAppender<ch.qos.logback.classic.spi.ILoggingEvent>) appender)
                  .getEncoder()
                  .encode(normalEvent),
              StandardCharsets.UTF_8);
      assertThat(withoutException)
          .contains("[requestId=none]")
          .contains("Request 42 failed password=****** Bearer ******")
          .endsWith(System.lineSeparator())
          .doesNotContain("%n", "%nopex");
    } finally {
      context.stop();
    }
  }
}
