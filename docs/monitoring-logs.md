# 개발 서버 로그 모니터링

관련 이슈: #35. Spring Boot `tomo-api`의 dev 로그를 stdout → `tomo.service`의 systemd journal → Grafana Alloy → Grafana Cloud Loki 순서로 전송한다.

## 수집 방식 비교 및 선정 근거

이슈 #35의 요구사항에 따라 애플리케이션 메모리 부하, 서버 보안 권한, 전송 안정성을 비교하여 로그 수집 방식을 결정했다.

| 비교 항목 | Logback 직접 전송 (Loki4j 등) | Promtail | Grafana Alloy (최종 선정) |
| --- | --- | --- | --- |
| **메모리·자원 격리** | ⚠️ JVM 힙 메모리 공유. 네트워크 단절 시 앱 메모리 큐 폭증 및 OOM 위험 | ✅ 경량 Go 프로세스 (독립 메모리) | ✅ 경량 Go 프로세스 (독립 메모리, 약 50MB 내외) |
| **권한 및 보안** | ⚠️ Spring Boot 애플리케이션에 Loki 인증 토큰 직접 주입 필요 | ✅ 전용 서비스 계정으로 권한 격리 | ✅ `alloy` 전용 계정 및 `root:alloy 640`으로 토큰 격리 |
| **전송 신뢰성** | ⚠️ 전송 실패 시 Spring 요청 지연 또는 앱 스레드 블로킹 위험 | ✅ journal 기반 비동기 추적 | ✅ systemd journal 비동기 수집 + cursor 관리 + 백오프 재시도 |
| **지속성·확장성** | ❌ 로그 전용 라이브러리로 메트릭 수집 불가 | ⚠️ Grafana 공식 Deprecated 예정 (유지보수 모드) | ✅ Grafana 차세대 표준 에이전트 (메트릭·로그 통합 파이프라인) |

- **선정 근거**: NCP Micro 서버(1 vCPU, 2GB RAM) 환경에서는 애플리케이션 프로세스의 안정성이 최우선이다. Logback appender 직접 전송은 네트워크 지연 시 JVM 힙 고갈을 유발할 수 있으므로 배제했다. Promtail은 유지보수 모드로 전환되었으므로, systemd journal을 독립적으로 읽어 앱에 부하를 주지 않고 향후 메트릭 파이프라인과 단일 에이전트로 통합 가능한 **Grafana Alloy**를 선정했다.

## 구성과 보관 정책

| 항목 | 설정 |
| --- | --- |
| Cloud | Grafana Cloud Hosted Logs / Loki Free |
| 수집 대상 | `_SYSTEMD_UNIT=tomo.service` |
| 라벨 | `job=tomo-service`, `environment=dev`, `application=tomo-api`, `level` |
| Cloud 보관 | 무료 플랜 기준 14일 |
| Cloud 수집 한도 | 월 50GB, 30일 환산 일평균 약 1.67GB |
| 로컬 journal | 영구 저장, 최대 사용량 목표 200MB, 파일당 50MB, 최대 보관 기간 7일 |
| 전송 버퍼 제한 | 디스크 WAL 비활성화(`wal.enabled=false`), 인메모리 재시도 최대 10회 제한 후 유실 드롭 |
| 초기 journal 조회 범위 | Alloy `max_age=12h` |

무료 한도는 [Grafana 요금표](https://grafana.com/pricing/)를 2026-11-03에 확인한 값이다. 실제 플랜과 사용량은 Cloud 화면에서 다시 확인한다. 로컬 크기 제한과 유닛 필터는 Cloud 월 수집량을 보장하지 않는다.

저널 설정은 [journald-tomo.conf](../infra/monitoring/journald/journald-tomo.conf)에 있다. 기본 journal namespace 전체에 적용되며 `tomo.service`만의 할당량이 아니다. 용량·기간은 최대 보관 목표이며 7일 전체를 보장하지 않는다. systemd는 활성 파일보다 보관 파일을 중심으로 정리하므로 실제 사용량이 순간적으로 목표를 넘을 수 있다. `RateLimitIntervalSec=30s`, `RateLimitBurst=10000`은 로그 폭증을 제한하며 제한된 로그는 유실될 수 있다.

## 전송 버퍼 용량 제한 및 실패 유실 정책

Micro 서버의 협소한 디스크 용량(20GB 내외)과 메모리를 보호하기 위해 [config.alloy](../infra/monitoring/alloy/config.alloy)에 다음 전송 버퍼 정책을 적용했다.

1. **디스크 WAL 비활성화 (`wal { enabled = false }`)**:
   - Alloy 기본 설정인 디스크 Write-Ahead Log는 전송 대기 로그를 디스크 세그먼트로 저장하여 디스크를 잠식할 위험이 있다. 이를 비활성화하여 디스크 공간을 영구 저널(200MB 목표) 외에 추가로 점유하지 않도록 제한한다.
2. **인메모리 버퍼 상한 및 재시도 제한 (`max_backoff_retries = 10`)**:
   - Grafana Cloud 연결 실패나 429 Quota 초과 시, `min_backoff_period=500ms`부터 `max_backoff_period=5m`까지 지수 백오프로 재시도한다.
   - 10회 연속 실패 시 해당 배치 로그는 인메모리 큐에서 영구 폐기(Drop)되어 Alloy 프로세스의 메모리 무한 증가를 방지한다.
   - 드롭된 로그 건수는 `loki_write_dropped_entries_total` 지표로 모니터링할 수 있다. 전송에 실패하여 유실된 로그라도 로컬 systemd 저널(최대 200MB / 7일)에 남아 있으므로 필요시 서버에서 `journalctl`로 복구 조회가 가능하다.

## 설치와 인증

서버에 저장소의 `infra/monitoring` 디렉터리 구조를 유지하여 복사하고 root 권한으로 실행한다.

```bash
sudo bash infra/monitoring/alloy/install-alloy.sh
```

설치 스크립트는 Grafana 저장소에서 Alloy를 설치하고 저널 정책을 배포한다. `/var/log/journal`을 `root:systemd-journal`, `2755`로 준비한 뒤 journald 재시작과 `journalctl --flush`를 수행한다. Alloy 사용자를 `systemd-journal` 그룹에 추가하고 [config.alloy](../infra/monitoring/alloy/config.alloy)를 배포한다.

스크립트 최초 실행은 빈 토큰 파일을 만들기 때문에 파일을 채우고 Alloy를 다시 시작해야 전송할 수 있다. `/etc/alloy/alloy.env`는 서버에서만 편집한다.

```dotenv
GCLOUD_HOSTED_LOGS_URL="https://logs-prod-030.grafana.net/loki/api/v1/push"
GCLOUD_HOSTED_LOGS_ID="1849388"
GCLOUD_RW_API_KEY="<서버에서 발급한 Logs 쓰기 토큰 입력>"
```

실제 토큰을 저장소·PR·문서·명령줄 인자로 남기지 않는다. 노출된 토큰은 Grafana Cloud Access Policies에서 폐기하고 필요한 Logs 쓰기 권한으로 새 토큰을 발급한 뒤 파일을 갱신한다. 서버에서 교체를 완료하기 전에는 토큰 교체가 끝난 것으로 보지 않는다.

```bash
sudo chown root:alloy /etc/alloy/alloy.env
sudo chmod 640 /etc/alloy/alloy.env
sudo systemctl daemon-reload
sudo systemctl restart alloy
sudo systemctl is-active alloy
sudo stat -c '%U:%G %a' /etc/alloy/alloy.env
```

환경 파일의 기대값은 `root:alloy 640`이다. 설치 스크립트를 다시 실행해도 기존 토큰 내용은 유지하며 소유자와 권한은 매번 보정한다. systemd drop-in은 `EnvironmentFile=-/etc/alloy/alloy.env`로 파일을 읽는다.

## 메시지와 예외 마스킹

[logback-spring.xml](../apps/api/src/main/resources/logback-spring.xml)은 local 프로필에서 컬러·DEBUG, 그 외 프로필에서 ISO-8601 시각·INFO 로그를 사용한다. 두 프로필 모두 다음 패턴으로 메시지와 예외를 마스킹한다.

```text
%maskedMsg(%msg){}%n%maskedMsg(${LOG_EXCEPTION_CONVERSION_WORD:-%wEx}){}%nopex
```

`MaskingMessageConverter`는 하위 패턴을 변환하는 composite converter이므로 `%maskedMsg`만 단독으로 쓰지 않는다. 빈 옵션 `{}`는 하위 패턴 종료 뒤의 `%n` 등을 별도 변환자로 파싱하도록 구분한다. `%nopex`는 마스킹되지 않은 예외가 자동으로 덧붙는 경로를 막는다. 원인 예외와 suppressed 예외도 예외 출력 안에서 마스킹한다.

마스킹 대상은 정의된 password·token·API 키 등의 키/값 패턴, Bearer 토큰과 JWT 패턴이다. Bearer는 `/`, `+`, `~`, `.`, `_`, `-`, 패딩 `=`까지 처리한다. 정규식은 모든 비밀값이나 개인정보를 식별할 수 없으므로 민감 정보를 애초에 로그로 기록하지 않는 것이 기본 정책이다. MDC, 별도 appender, 외부 라이브러리의 직접 출력까지 일괄 보호하는 기능은 아니다.

## 서버 검증

```bash
sudo systemctl status tomo --no-pager
sudo journalctl -u tomo.service -n 20 --no-pager
sudo journalctl --disk-usage
sudo ls -ld /var/log/journal
sudo systemctl status alloy --no-pager
curl -fsS http://127.0.0.1:12345/metrics | grep loki_source_journal_target_lines_total
curl -fsS http://127.0.0.1:12345/metrics | grep -E 'loki_write_request_duration_seconds_count|loki_write_sent_entries_total|loki_write_dropped_entries_total'
```

수집 라인·전송 엔트리 카운터가 증가하고 HTTP `204` 응답이 발생하는지 확인한다. dropped 지표는 모든 reason을 확인한다. 특정 reason의 값이 0인 것만으로 전체 무손실을 증명할 수 없다. 실행 중 메모리는 `systemctl status alloy`의 관측값이며 고정적인 메모리 상한은 아니다.

사용자가 제공한 2026-11-02 서버 기록에서는 Alloy active 상태, journal 수집 라인 31건, Loki 전송 엔트리 239건과 HTTP 204 응답이 관측됐다. 이 기록은 현재 수정한 XML을 배포·검증했다는 증거는 아니다. 새 JAR 배포 후 일반 메시지가 비어 있지 않은지, 가짜 테스트 값으로 메시지·예외가 마스킹되는지 별도로 확인한다. 실제 비밀값으로 시험하지 않는다.

Grafana Cloud Explore에서 해당 스택의 Logs 데이터소스를 선택하고 최근 시간 범위로 조회한다.

```logql
{job="tomo-service", environment="dev"}
```

```logql
{job="tomo-service"} |= "ERROR"
```

```logql
{job="tomo-service"} |= "Exception"
```

현재 `level`은 journal 우선순위에서 가져온 값으로, 메시지 본문의 Spring 로그 레벨과 항상 같지는 않다. 애플리케이션 ERROR 조회는 본문 필터도 함께 사용한다.

## requestId 기반 요청 로그 추적

관련 이슈: #46. 응답의 `meta.requestId`와 로그는 서블릿 컨테이너의 `request.getRequestId()`를 사용한다. 별도 ID 생성이나 클라이언트 헤더 수신 없이 기존 응답 계약을 유지한다.

`RequestLoggingConfig`는 현재 Security 필터 순서보다 한 단계 앞에 `RequestIdFilter`를 등록한다. 필터는 요청 처리 동안 MDC의 `requestId`를 설정하고, 정상 반환·인증 거부·예외 발생 시 모두 `finally`에서 정리한다. 이미 있던 MDC 값은 복원하며 다른 MDC 항목은 유지한다. REQUEST·ASYNC·ERROR 디스패치에 적용하고, 중첩 ERROR 디스패치는 현재 스레드의 컨텍스트를 유지한다.

각 기능에서는 기존 SLF4J Logger로 메시지만 기록한다.

```java
private static final Logger LOG = LoggerFactory.getLogger(MyService.class);

LOG.debug("처리 단계 확인");
LOG.info("작업 완료");
LOG.warn("외부 서비스 재시도 한도 도달");
LOG.error("작업 처리 실패", exception);
```

Logback은 local·dev 모두 `[requestId=...]`를 메시지 앞에 자동 출력한다. 시작 로그·스케줄러 등 요청 컨텍스트가 없는 로그는 `[requestId=none]`으로 출력한다. 기존에 메시지에 직접 쓴 requestId는 계속 동작하지만 중복 표시될 수 있다.

| 레벨 | 사용 기준 |
| --- | --- |
| DEBUG | 개발 중 필요한 상세 처리 흐름 |
| INFO | 주요 정상 업무 이벤트 |
| WARN | 재시도 소진 등 확인이 필요한 이상 상황 |
| ERROR | 예상하지 못한 서버 처리 실패. 예외 객체를 함께 전달 |

local은 `com.tomo` DEBUG, 그 외 프로필은 INFO 이상을 출력한다. 토큰 만료·잘못된 입력처럼 예상 가능한 실패를 일괄 ERROR로 기록하지 않는다. 예외는 최종 처리 계층에서 한 번 기록하며, 로그를 남기고 다시 던져 같은 예외를 중복 기록하지 않는다. 요청 헤더·본문 전체, 토큰·비밀번호·쿠키·개인정보는 기록하지 않는다. 기존 메시지·예외 마스킹은 보조 수단이다.

현재 애플리케이션에는 `@Async`, MVC `Callable`·`DeferredResult` 사용이 없다. ASYNC 재디스패치 스레드에는 필터가 MDC를 다시 설정하지만, 별도 작업 스레드 실행까지 자동 전파하지는 않는다. 이런 기능을 도입할 때는 해당 실행기의 TaskDecorator 등으로 제출 시 컨텍스트 복사와 실행 후 복원·정리를 추가해야 한다.

Grafana Explore에서 응답의 ID를 복사하여 기존 Logs 데이터소스와 요청 시간 범위에서 검색한다.

```logql
{job="tomo-service", environment="dev"} |= "[requestId=실제-응답-ID]"
```

requestId는 요청마다 달라지므로 Loki 인덱스 라벨로 추가하지 않고 본문 필터로 조회한다. 기존 stdout → journal → Alloy → Loki 경로를 그대로 사용한다. 이 필터는 요청마다 별도 접속 로그를 생성하지 않으므로 해당 요청에서 기록한 로그가 없으면 검색 결과도 없다. 401·403 응답 자체가 WARN·ERROR 로그 생성을 의미하지 않는다.

배포 후에는 테스트 요청으로 응답 ID와 journal의 `[requestId=...]` 일치를 확인하고, 같은 ID를 Grafana에서 검색한다. 정상·401·403·500 경로와 후속 요청의 ID가 섞이지 않는지 확인한다. 장애를 만들기 위해 실제 DB를 중단하거나 실제 비밀값을 사용하지 않는다. 로컬 테스트 통과와 개발 서버 배포 후 검색 검증은 별도로 기록한다.

## 장애 대응과 복구

| 증상 | 확인·대응 |
| --- | --- |
| 수집 카운터가 증가하지 않음 | `tomo.service` 유닛명, journal 생성, Alloy 그룹 권한과 서비스 재시작 여부 확인 |
| 401·403 전송 오류 | 서버 환경 파일의 ID·토큰·Logs 쓰기 권한 확인 후 Alloy 재시작 |
| 429·전송 재시도 | Cloud 한도·수집률·네트워크 확인. 재시도 소진 시 유실 가능 |
| Loki에 로그가 없음 | 데이터소스·조회 시간·라벨·수집/전송 카운터와 Alloy journal 확인 |
| 수정 배포 후 문제가 생김 | 직전 검증된 JAR 또는 Alloy 설정으로 복원하고 해당 서비스 재시작 |

```bash
sudo journalctl -u alloy.service -n 100 --no-pager
sudo systemctl restart alloy
sudo systemctl is-active alloy
```

장애 중 journal은 로컬 보관 정책의 범위에서 순환한다. 보관 기간·용량 초과 또는 수집 범위 밖 로그를 자동으로 전량 복구한다고 가정하지 않는다. Alloy 저장 경로의 cursor와 재시도 동작을 확인하고 재전송에 따른 중복 가능성도 점검한다.

Cloud의 Usage 또는 Cost Management and Billing 화면에서 Logs 수집량을 주간 점검한다. 한도에 가까우면 불필요한 로그 수준·빈도를 줄이고 월말 수집량을 추정한다. 설정 변경 후 Alloy 재시작과 Cloud 조회까지 다시 확인한다.

## 참고

- [Logback 패턴과 composite converter](https://logback.qos.ch/manual/layouts.html)
- [systemd 저널 보관 정책](https://www.freedesktop.org/software/systemd/man/latest/journald.conf.html)
- [Grafana Alloy journal 수집](https://grafana.com/docs/alloy/latest/reference/components/loki/loki.source.journal/)
- [Grafana Alloy loki.write 컴포넌트](https://grafana.com/docs/alloy/latest/reference/components/loki/loki.write/)

운영 절차와 관측 기록의 원본은 이 작업에서 사용자가 제공한 2026-11-02 서버 구성 자료이며, 저장소 반영일은 2026-11-03이다. 실제 토큰은 제거하고 현재 코드에 맞춰 마스킹·재설치 절차를 보완했다.
