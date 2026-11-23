package com.tomo;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class TomoApiApplicationTest {

  @Autowired private DataSource dataSource;

  @MockitoBean private RedissonClient redissonClient;

  @LocalServerPort private int port;

  @Test
  @DisplayName("기본 설정으로 API 서버가 임의의 포트에서 시작된다")
  void startsServer() {
    // when
    int actualPort = port;

    // then
    assertThat(actualPort).isPositive();
  }

  @Test
  @DisplayName("테스트 데이터소스가 MySQL 컨테이너에 연결된다")
  void connectsToMySql() throws SQLException {
    // when
    try (Connection connection = dataSource.getConnection()) {
      String databaseProduct = connection.getMetaData().getDatabaseProductName();

      // then
      assertThat(databaseProduct).isEqualTo("MySQL");
      assertThat(connection.isValid(2)).isTrue();
    }
  }
}
