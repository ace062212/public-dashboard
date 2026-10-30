# 서울 실시간 대시보드

기상청 단기예보 · 환경부 대기오염도 · 서울교통공사 역사 혼잡도를 한 화면에 모아보는 Spring Boot 대시보드입니다.

서비스 키가 없어도 바로 기동되며, 그 상태에서는 **목(mock) 데이터**로 화면과 API 전체가 동작합니다. 키를 넣으면 실데이터로 전환됩니다.

## 기술 스택

| 항목 | 사용 |
|---|---|
| 언어 / 런타임 | Java 21 (가상 스레드 활성화) |
| 프레임워크 | Spring Boot 3.5.16 (web, cache, validation) |
| 캐시 | Caffeine (TTL + 백그라운드 갱신) |
| HTTP 클라이언트 | Spring `RestClient` |
| 프런트엔드 | 정적 HTML/CSS/vanilla JS + ECharts (CDN) |
| 빌드 | Maven |

## 빠른 시작

```bash
mvn spring-boot:run
# 브라우저: http://localhost:8080
```

키 없이 실행하면 카드 우측 배지가 `목 데이터`로 뜨고, `/api/health/upstreams` 에서 각 소스 상태를 확인할 수 있습니다.

## 실데이터 연동

[공공데이터포털](https://www.data.go.kr/) 에서 아래 서비스를 신청한 뒤 발급받은 인증키를 환경 변수로 넣습니다.

| 데이터 | 서비스 | 환경 변수 |
|---|---|---|
| 단기예보 | 기상청 기초생활기상(`getVilageFcst`) | `WEATHER_SERVICE_KEY` |
| 대기오염도 | 환경부 측정정보서비스(권역별 실측) | `FINE_DUST_SERVICE_KEY` |
| 지하철 혼잡도 | 서울교통공사 역사별 혼잡도 | `SUBWAY_SERVICE_KEY` |

```bash
export WEATHER_SERVICE_KEY='...'
export FINE_DUST_SERVICE_KEY='...'
export SUBWAY_SERVICE_KEY='...'
mvn spring-boot:run
```

> **주의**: 세 서비스의 실제 요청 URL·파라미터명은 포털 API 문서 기준으로 다를 수 있습니다.
> 모든 주소와 경로는 `application.yml` 의 `public-data.*` 에서 덮어쓸 수 있도록 빼두었으니, 키를 받고 나서
> 한 번 확인 후 맞춰주세요. 개별 환경 변수(`WEATHER_BASE_URL`, `WEATHER_PATH` 등)로도 조정 가능합니다.

키가 하나라도 빠진 상태에서 `public-data.mock-when-key-missing=false` 로 두면 기동이 실패합니다(조용히 목 데이터로
실데이터를 흉내내지 않도록 하기 위한 기본 정책).

## REST API

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/dashboard` | 세 소스 + 날씨 요약 한 번에 |
| GET | `/api/weather` | 단기예보 항목 목록 |
| GET | `/api/fine-dust?realm=11` | 권역별 실측 농도 (realm: 2자리 권역코드) |
| GET | `/api/subway?stationName=강남` | 역사 혼잡도 (역 이름으로 좁히기) |
| POST | `/api/refresh` | 캐시를 비우고 즉시 재호출 |
| GET | `/api/health/upstreams` | 소스별 LIVE/MOCK/FALLBACK, 성공·실패 횟수 |

응답의 `source` 값은 `LIVE`(실데이터), `MOCK`(키 미설정), `FALLBACK`(업스트림 장애 대체) 입니다.

## 설정

`src/main/resources/application.yml` 참고. 주로 건드릴 값:

| 키 | 기본값 | 설명 |
|---|---|---|
| `public-data.cache-ttl` | `60s` | 캐시 유지 시간 |
| `public-data.refresh-interval` | `60s` | 백그라운드 사전 갱신 주기 |
| `public-data.connect-timeout` / `read-timeout` | `3s` / `5s` | 업스트림 호출 타임아웃 |
| `public-data.weather.nx` / `ny` | `60` / `127` | 조회 격자좌표 (서울 시청 기준) |
| `public-data.fine-dust.realm-code` | `11` | 기본 권역 코드 |
| `public-data.subway.line-id` | `02` | 기본 호선 |

## 아키텍처

```
Browser ──> DashboardController ──> DashboardService(@Cacheable) ──> WeatherClient ─┐
                              │                                        FineDustClient ├─> 공공API
                              │                                        SubwayClient ──┘
                              └── RefreshScheduler(주기적 선갱신)          │
                                                            UpstreamHealthRegistry(상태 기록)
```

- **캐시**: 화면이 1분마다 새로고침해도 공공API 는 TTL 만큼만 호출됩니다.
- **선갱신**: `RefreshScheduler` 가 주기적으로 캐시를 채워, 만료 직후 요청이 느려지는 상황을 피합니다.
- **장애 대체**: 업스트림이 죽으면 목 데이터로 `FALLBACK` 을 반환하고 상태를 기록합니다(500 으로 터뜨리지 않음).
- **인코딩**: 한글 파라미터는 `UriComponentsBuilder...encode().build().toUri()` 로 만든 `URI` 객체를 그대로 넘깁니다.
  문자열로 넘기면 `RestClient` 가 한 번 더 인코딩해 `%25EC...` 으로 깨집니다(회귀 테스트로 고정).

## 테스트

```bash
mvn test
```

- 클라이언트 3종: 목 모드, 파싱, 파라미터 인코딩(한글 `세종대로` 이중인코딩 검증), 500/비정상 헤더 폴백
- 서비스: 캐시 적중/무효화, 날씨 요약 선두값 선정
- API: 200/400/404/405 경로, 한글 역 이름 필터, UTF-8 응답
- 실제 내장 서버 기동: 정적 페이지·자산·API 왕복

## 운영 시 유의

- 인증키는 소스에 커밋하지 말고 환경 변수로만 주입하세요. `.env*`, `*.key`, `*.pem` 은 `.gitignore` 에 있습니다.
- 업스트림 응답은 캐시에만 머물고 DB 에 저장하지 않습니다. 이력 수집이 필요하면 저장 계층을 추가하세요.
- 개인 정보는 취급하지 않습니다.
