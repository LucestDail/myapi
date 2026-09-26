# MyAPI - 백엔드 API 서버

Spring Boot 기반의 REST API 서버로, 주식 정보, 날씨, 뉴스, 시스템 모니터링, 생산성 도구(할 일, 타이머), 알림 등 다양한 기능을 제공합니다.

이 API는 웹 프론트엔드, iOS/Android 모바일 앱에서 사용할 수 있도록 설계되었습니다.

---

## 운영 상태 (2026-09-26) — 프론트엔드 전면 개편

바닐라 JS(정적 파일 30개, 빌드 도구 없음) → **Vue 3 + Vite + Pinia + TypeScript**.
소스는 `frontend/`, Maven 빌드(`mvn package`)에 `frontend-maven-plugin`으로 통합되어
있어 별도 빌드 단계 없이 jar 하나로 나온다. 산출물(`frontend/dist`)과
`node_modules`는 커밋하지 않는다(`.gitignore`) — 체크아웃 직후 `mvn package`만
돌리면 node 설치부터 빌드까지 끝난다.

**왜 다시 짰나**: `api.js`에 `fetchApi`/`postApi` 등이 다 있었는데 쓰는 파일이
0개였다 — `fetch()`를 27곳에서 직접 호출하고 `X-User-Id` 헤더를 17번 복붙했다.
인라인 `onclick` 70개가 `window.*` 전역 57개를 강제했다. 빌드 도구가 없어 타입
검사도 캐시 무효화도 불가능했다.

**이식 중 발견·수정한 원본 결함**:
- Emergency 섹션 페이지 크기 상수(10)를 렌더 함수가 무시하고 20 하드코딩
- News 섹션이 야후/연합 페이지 인덱스를 공유해 한쪽 넘기면 같이 넘어감
- Timer 최초 생성 API 호출이 어디서도 안 일어나 항상 500(조용히 무시됨)
- Todo priority를 문자열로 보내 서버 Integer 필드에 반영된 적 없음
- 티커 추가·유튜브 URL 변경이 새로고침해야 반영되던 버그 2건(SSE 재연결
  타이밍 의존 + "보낸 값"과 "이전 값" 비교 실수)

**레이아웃 재작업 — 처음엔 원본과 다른 화면을 만들었다.** 상단 헤더 + 카드
그리드로 짰는데, 원본은 `grid-template-columns: 1fr 420px`(좌측 유튜브 1fr,
우측 420px 고정 패널에 섹션이 점선 구분 목록으로 세로 쌓임) 2단 구조였고 탭도
4개가 아니라 **5개**(사회·금융·생활·생산성·시스템)였다. `system` 섹션을 "탭 없는
고아"로 오판해 `life`에 합쳤던 것도 되돌렸다.

**모달 스크롤 — 세 번 만에 근본 원인을 잡았다.** AI 리포트 생성 중 좌측
체크박스 패널이 우측 스트리밍 본문과 같이 밀려 내려가는 버그. 버튼을 푸터로
옮기고 `.left`의 `overflow-y:auto`를 없앤 것만으로는 안 고쳐졌다 — 진짜 원인은
`ModalShell`이 `max-height`만 있고 `height`가 없어서, 본문이 짧을 때 자식의
`height:100%`가 CSS 스펙상 순환 참조로 **무시**되는 것이었다. 원본이
`style="height:95vh"`로 처음부터 고정 높이였던 이유가 이것. `ModalShell`에
`height` prop을 추가해 AI 리포트 모달에서만 고정 높이를 준다(다른 모달은
콘텐츠에 맞춰 자동 크기 유지).

**AI 리포트 자체도 개편**: 진행률이 2초 타이머로 도는 가짜였다 —
`POST /api/ai-report/generate/stream`을 신설해 서버가 실제 단계(수집원별·AI
호출·이력저장)를 SSE로 흘린다. 생성 파라미터(Temperature 등) 입력은 판단 근거가
없었고 게이트웨이가 OpenRouter로 번역해 일부는 전달되지도 않아 서버 고정값으로
옮겼다.

배포 검증: 테스트 165개 → 재기동 15~21초·재시작 0회 → 참조 에셋 실제 200(waynai가
9일간 겪은 base 경로 함정을 배포 전 체크리스트로 차단) → SSE 실데이터 확인.

---

## 운영 상태 (2026-09-25) — 배포 완료, 행 축적 버그 정지

홈랩 `.25` 에 **2026-09-25 배포 완료**. 라이브 = 저장소 `510ecc0`.

### 무엇을 고쳤나
라이브가 `b608731`(09-03)에 머물러 있어서 **익명 요청마다 사용자 행이 쌓이는 버그가 계속 돌고 있었다.**
9/16 에 DB 를 1.6MB 로 정리했는데 8일 만에 `user_profiles` 가 **6,912행** 다시 쌓였다(하루 약 860행).

반영된 수정:
- `db09364` 익명 요청마다 사용자를 만들어 736만 행이 쌓였다
- `8152906` 익명 저장을 끄니 어떤 경로로도 프로필이 안 생겼다

**검증**: 재기동 후 익명 요청 10회 → `user_profiles` 6,912행 불변. 버그 정지 확인.
롤백 jar: `target/myapi-0.0.1-SNAPSHOT.jar.bak-20260925-101916`

### 다음 세션이 알아야 할 것
- **`~/Workspace/myapi/myapi/` 자체가 배포 디렉터리**다(저장소명 아래 한 단계 더 중첩). 그 안의 `target/*.jar` 를 systemd 가 직접 실행한다. 반영하려면 서버 소스 갱신 + 재빌드 + 재기동이 필요하다.
- 서버는 `git pull` 이 안 된다(HTTPS 인증). **git bundle** 로 옮긴다:
  ```bash
  git bundle create /tmp/myapi.bundle <서버SHA>..main
  scp -O /tmp/myapi.bundle <서버>:/tmp/
  ssh <서버> 'cd ~/Workspace/myapi/myapi && git fetch /tmp/myapi.bundle main && git merge --ff-only FETCH_HEAD'
  ```
- 🔴 **운영 키 소스는 `/etc/myapi.env`** 다. README 의 옛 경로(`api-keys.conf`)를 따라가면 키가 하나도 안 잡힌 채 뜬다.
- 🔴 **인증이 꺼져 있다** — `/etc/myapi.env` 에 `MYAPI_API_KEY` 항목 자체가 없어 `ApiKeyRestFilter` 가 fail-open 으로 전부 통과시킨다. nginx 도 `/myapi/` 에 gwauth 를 안 걸고 rate-limit 만 건다. 생태계 최다 호출처(누적 68,433회)인데 방어가 이것뿐이다.
- ⚠️ `MYAPI_DAILY_REPORT_USERS` 를 비우고 전체로 돌리면 과거 대량 Gemini 호출 사고가 재현된다.

---

## 📋 목차

- [기술 스택](#기술-스택)
- [설치 및 실행](#설치-및-실행)
- [API 키 설정](#api-키-설정)
- [API 엔드포인트](#api-엔드포인트)
  - [1. 대시보드 API](#1-대시보드-api)
  - [2. 주식 API (Finnhub)](#2-주식-api-finnhub)
  - [3. 날씨 API](#3-날씨-api)
  - [4. 위치 기반 날씨 API](#4-위치-기반-날씨-api)
  - [5. RSS 뉴스 API](#5-rss-뉴스-api)
  - [6. 시스템 모니터링 API](#6-시스템-모니터링-api)
  - [7. 생활정보 API](#7-생활정보-api)
  - [8. 할 일 관리 API](#8-할-일-관리-api)
  - [9. 타이머/포모도로 API](#9-타이머포모도로-api)
  - [10. 알림 API](#10-알림-api)
  - [11. 사용자 설정 API](#11-사용자-설정-api)
  - [12. 소셜/실시간 API](#12-소셜실시간-api-apisocial)
  - [13. AI 리포트 API](#13-ai-리포트-api-apiai-report)
- [인증 및 사용자 식별](#인증-및-사용자-식별)
- [응답 형식](#응답-형식)
- [에러 처리](#에러-처리)
- [캐싱 전략](#캐싱-전략)
- [프로젝트 구조](#프로젝트-구조)

---

## 기술 스택

**백엔드**
- **Java 17**
- **Spring Boot 3.3.6**
- **Maven**
- **SQLite** (데이터베이스)
- **JPA/Hibernate**
- **Server-Sent Events (SSE)** (실시간 스트리밍)

**프론트엔드** (`frontend/`, 2026-09-26 개편)
- **Vue 3 + TypeScript + Vite**
- **Pinia** (상태 관리 — 대시보드 데이터 + SSE 연결, UI 상태)
- `frontend-maven-plugin`으로 `mvn package`에 통합. 산출물은 커밋하지 않는다.

---

## 설치 및 실행

### Ubuntu 24.04 설치

```bash
# 시스템 업데이트
sudo apt update && sudo apt upgrade -y

# Java 17 설치
sudo apt install openjdk-17-jdk -y

# Maven 설치
sudo apt install maven -y

# Git 설치 (선택사항)
sudo apt install git -y

# 방화벽 설정 (선택사항)
sudo ufw allow 8080/tcp
```

### 빌드 및 실행

```bash
# 실행 권한 부여
chmod +x build.sh run.sh dashboard.sh

# 빌드
./build.sh

# 실행 (포그라운드)
./run.sh

# 실행 (백그라운드)
./run.sh start

# 중지
./run.sh stop

# 재시작
./run.sh restart

# 대시보드 (터미널)
./dashboard.sh
```

---

## API 키 설정

다음 외부 API 서비스의 키가 필요합니다:

| 서비스 | 링크 | 제한 |
|--------|------|------|
| Finnhub | https://finnhub.io/register | 30 calls/sec |
| OpenWeatherMap | https://openweathermap.org/api | 60 calls/min |
| 공공데이터포털 (미세먼지) | https://www.data.go.kr | - |

### 환경 변수 설정

> ⚠️ **파일 이름은 `/etc/myapi/conf` 다.** 이 문서는 2026-09-16 까지 `api-keys.conf` 라고
> 적어 두었지만 `run.sh`·`build.sh` 가 실제로 읽는 것은 **`/etc/myapi/conf`** 이고,
> 그대로 따라 하면 **키가 하나도 안 잡힌 채 뜬다**(그 상태로도 기동은 된다).
>
> ⚠️ 그리고 여기 적혀 있던 키는 **3개뿐이었는데 코드는 15개를 읽는다.** 빠진 키는
> 조용히 빈 문자열이 되어 **그 기능만 죽는다** — 아래가 전수 목록이다.

```bash
# 설정 디렉토리 생성
sudo mkdir -p /etc/myapi

# 설정 파일 생성 (템플릿은 저장소의 conf.example 참고)
sudo tee /etc/myapi/conf > /dev/null << 'EOF'
# ── 외부 API 키 ──────────────────────────────────────────────
export FINNHUB_API_KEY="your_finnhub_key"            # 주식/암호화폐
export OPENWEATHER_API_KEY="your_openweather_key"    # 날씨
export AIRKOREA_API_KEY="your_airkorea_key"          # 대기질(공공데이터포털)
export TRAFFIC_API_KEY="your_traffic_key"            # 교통(공공데이터포털)
export EMERGENCY_API_SERVICE_KEY="your_emergency_key" # 긴급재난(공공데이터포털)

# ── LLM (osh-ai-gateway 경유) ────────────────────────────────
export GEMINI_API_KEY="your_gemini_key"              # 게이트웨이 미사용 시 직결용
export GEMINI_GATEWAY_BASE_URL="http://127.0.0.1/llm"
export GEMINI_GATEWAY_TOKEN="gateway_internal_token"
export GEMINI_SERVICE_ID="myapi"

# ── 뉴스 DB (osh 와 공유) ────────────────────────────────────
export NEWS_DB_JDBC_URL="jdbc:postgresql://..."
export NEWS_DB_USERNAME="news_user"
export NEWS_DB_PASSWORD="..."

# ── 서비스 자체 ──────────────────────────────────────────────
export MYAPI_API_KEY=""                              # 비우면 ApiKeyRestFilter 가 통과시킨다
export SERVER_PORT="8080"
export SERVER_SERVLET_CONTEXT_PATH="/myapi"
EOF

# 파일 보안 설정
sudo chmod 600 /etc/myapi/conf

# 환경 변수 로드
source /etc/myapi/conf
```

또는 실행 스크립트(`run.sh`)에서 자동으로 로드된다.

> 🔴 `MYAPI_API_KEY` 를 **비우면 인증 없이 열린다**(`ApiKeyRestFilter` 가 키 미설정 시 통과).
> `.25` 는 nginx 앞단에서 rate limit 만 걸고 있으므로, 외부에 노출한다면 이 값을 설정할 것.

---

## API 엔드포인트

기본 URL: `http://localhost:8080`

모든 API는 `application/json` 형식으로 요청/응답합니다.

---

### 1. 대시보드 API

실시간 대시보드 데이터를 SSE(Server-Sent Events)로 스트리밍하거나 REST API로 조회할 수 있습니다.

#### 1.1 SSE 스트림 연결

**GET** `/api/dashboard/stream`

실시간으로 주식, 날씨, 뉴스, 시스템 정보를 스트리밍합니다.

**응답 형식:** `text/event-stream`

**이벤트 타입:**
- `connected`: 연결 확인
- `dashboard`: 전체 데이터 업데이트 (60초마다)
- `system`: 시스템 데이터 업데이트 (5초마다)
- `alert`: 알림 이벤트

**예시:**
```bash
curl -N "http://localhost:8080/api/dashboard/stream"
```

**JavaScript 예시:**
```javascript
const eventSource = new EventSource('http://localhost:8080/api/dashboard/stream');

eventSource.addEventListener('connected', (e) => {
  console.log('Connected:', JSON.parse(e.data));
});

eventSource.addEventListener('dashboard', (e) => {
  const data = JSON.parse(e.data);
  console.log('Dashboard update:', data);
});

eventSource.addEventListener('alert', (e) => {
  const alert = JSON.parse(e.data);
  console.log('Alert:', alert);
});
```

#### 1.2 현재 데이터 조회

**GET** `/api/dashboard/data`

현재 대시보드 데이터의 스냅샷을 조회합니다 (SSE 없이).

**응답:**
```json
{
  "type": "full",
  "timestamp": "2024-01-17T00:00:00Z",
  "stocks": {
    "quotes": [
      {
        "symbol": "AAPL",
        "name": "Apple Inc.",
        "currentPrice": 150.25,
        "change": 2.5,
        "percentChange": 1.69,
        "highPrice": 152.0,
        "lowPrice": 149.5,
        "openPrice": 150.0,
        "previousClose": 147.75
      }
    ],
    "fetchedAt": "2024-01-17T00:00:00Z"
  },
  "weather": [
    {
      "city": "Seoul",
      "cityKo": "서울",
      "temperatureCelsius": 15.5,
      "humidity": 65,
      "weather": "Clear",
      "icon": "01d"
    }
  ],
  "news": {
    "yahooNews": [...],
    "yonhapNews": [...],
    "fetchedAt": "2024-01-17T00:00:00Z"
  },
  "system": {
    "cpuUsage": 25.5,
    "memoryUsagePercent": 45.2,
    "memoryUsed": 2048000000,
    "memoryTotal": 4096000000,
    "heapUsagePercent": 60.3,
    "heapUsed": 512000000,
    "heapMax": 1024000000,
    "threadCount": 45,
    "gcCount": 120,
    "gcTime": 5000,
    "uptimeMillis": 3600000
  }
}
```

#### 1.3 대시보드 설정 조회

**GET** `/api/dashboard/config`

현재 대시보드 설정을 조회합니다.

#### 1.4 대시보드 설정 업데이트

**POST** `/api/dashboard/config`

대시보드 설정을 업데이트합니다.

**요청 본문:**
```json
{
  "stocks": {
    "symbols": ["AAPL", "MSFT", "GOOGL"],
    "enabled": true
  },
  "weather": {
    "cities": ["Seoul", "Busan"],
    "enabled": true
  },
  "news": {
    "sources": ["yahoo", "yonhap"],
    "enabled": true
  }
}
```

#### 1.5 연결 수 조회

**GET** `/api/dashboard/connections`

현재 SSE 연결 수를 조회합니다.

**응답:**
```json
5
```

---

### 2. 주식 API (Finnhub)

#### 2.1 실시간 주가 조회

**GET** `/api/finnhub/quote?symbol={symbol}`

**파라미터:**
- `symbol` (필수): 주식 심볼 (예: AAPL, MSFT, GOOGL)

**응답:**
```json
{
  "currentPrice": 150.25,
  "change": 2.5,
  "percentChange": 1.69,
  "highPrice": 152.0,
  "lowPrice": 149.5,
  "openPrice": 150.0,
  "previousClose": 147.75,
  "timestamp": 1705449600
}
```

**예시:**
```bash
curl "http://localhost:8080/api/finnhub/quote?symbol=AAPL"
```

#### 2.2 회사 프로필 조회

**GET** `/api/finnhub/profile?symbol={symbol}`

**응답:**
```json
{
  "name": "Apple Inc.",
  "ticker": "AAPL",
  "exchange": "NASDAQ",
  "finnhubIndustry": "Technology",
  "weburl": "https://www.apple.com",
  "logo": "https://...",
  "marketCapitalization": 2500000000000
}
```

#### 2.3 시장 뉴스 조회

**GET** `/api/finnhub/news?category={category}`

**파라미터:**
- `category` (선택, 기본값: `general`): 카테고리 (`general`, `forex`, `crypto`, `merger`)

**응답:** 뉴스 배열

#### 2.4 회사 뉴스 조회

**GET** `/api/finnhub/company-news?symbol={symbol}&from={from}&to={to}`

**파라미터:**
- `symbol` (필수): 주식 심볼
- `from` (필수): 시작 날짜 (YYYY-MM-DD)
- `to` (필수): 종료 날짜 (YYYY-MM-DD)

#### 2.5 애널리스트 추천 조회

**GET** `/api/finnhub/recommendation?symbol={symbol}`

#### 2.6 재무 정보 조회

**GET** `/api/finnhub/financials?symbol={symbol}`

P/E 비율, 52주 고가/저가 등의 기본 재무 정보를 반환합니다.

#### 2.7 경쟁사 조회

**GET** `/api/finnhub/peers?symbol={symbol}`

**응답:** 경쟁사 심볼 배열
```json
["MSFT", "GOOGL", "AMZN", "META"]
```

#### 2.8 캐시 상태 조회

**GET** `/api/finnhub/cache/status`

**응답:**
```json
{
  "quoteCacheSize": 10,
  "profileCacheSize": 5,
  "lastRefresh": "2024-01-17T00:00:00Z"
}
```

---

### 3. 날씨 API

#### 3.1 모든 도시 날씨 조회

**GET** `/api/weather`

캐시된 모든 도시의 날씨 정보를 반환합니다.

**응답:** `CityWeatherResponse` 배열

#### 3.2 특정 도시 날씨 조회

**GET** `/api/weather/{city}`

**파라미터:**
- `city` (필수): 도시 이름 (소문자, 예: `seoul`, `tokyo`)

**지원 도시:** Seoul, Busan, Incheon, Daegu, Daejeon, Gwangju, Suwon, Ulsan, Jeju, Changwon

**응답:**
```json
{
  "city": "Seoul",
  "cityKo": "서울",
  "country": "KR",
  "lat": 37.5665,
  "lon": 126.9780,
  "weather": "Clear",
  "description": "clear sky",
  "icon": "01d",
  "temperature": 288.65,
  "temperatureCelsius": 15.5,
  "feelsLike": 288.0,
  "feelsLikeCelsius": 14.8,
  "tempMin": 285.0,
  "tempMax": 290.0,
  "humidity": 65,
  "pressure": 1013,
  "windSpeed": 3.5,
  "windDeg": 180,
  "cloudiness": 0,
  "visibility": 10000,
  "sunrise": "2024-01-17T00:30:00Z",
  "sunset": "2024-01-17T09:15:00Z",
  "fetchedAt": "2024-01-17T00:00:00Z"
}
```

#### 3.3 사용 가능한 도시 목록

**GET** `/api/weather/cities/list`

**응답:**
```json
["seoul", "busan", "incheon", "daegu", "daejeon", "gwangju", "suwon", "ulsan", "jeju", "changwon"]
```

#### 3.4 캐시 상태 조회

**GET** `/api/weather/cache/status`

---

### 4. 위치 기반 날씨 API

#### 4.1 현재 위치 날씨 조회

**GET** `/api/location/weather`

현재 위치 기반 날씨 정보를 반환합니다 (wttr.in 사용, 60초 캐시).

**응답:**
```json
{
  "location": "Seoul, South Korea",
  "temperature": 15.5,
  "condition": "Clear",
  "humidity": 65,
  "windSpeed": 3.5,
  "fetchedAt": "2024-01-17T00:00:00Z"
}
```

---

### 5. RSS 뉴스 API

모든 RSS 피드는 **10분간 캐시**됩니다.

#### 5.1 Yahoo Finance 시장 뉴스

**GET** `/api/rss/yahoo/market`

#### 5.2 Yahoo Finance 주식별 뉴스

**GET** `/api/rss/yahoo/stock?symbol={symbol}`

**파라미터:**
- `symbol` (필수): 주식 심볼

#### 5.3 연합뉴스 전체

**GET** `/api/rss/yonhap/all`

#### 5.4 연합뉴스 경제

**GET** `/api/rss/yonhap/economy`

#### 5.5 연합뉴스 정치

**GET** `/api/rss/yonhap/politics`

#### 5.6 연합뉴스 IT/과학

**GET** `/api/rss/yonhap/it`

#### 5.7 커스텀 RSS 피드

**GET** `/api/rss/custom?url={url}`

**파라미터:**
- `url` (필수): RSS 피드 URL

**예시:**
```bash
curl "http://localhost:8080/api/rss/custom?url=https://feeds.reuters.com/reuters/topNews"
```

#### 5.8 RSS 캐시 상태

**GET** `/api/rss/cache/status`

**응답 형식:**
```json
{
  "feedUrl": "https://feeds.reuters.com/reuters/topNews",
  "feedTitle": "Reuters Top News",
  "source": "reuters",
  "itemCount": 20,
  "items": [
    {
      "title": "Article Title",
      "link": "https://...",
      "description": "Summary...",
      "pubDate": "Fri, 17 Jan 2024 00:00:00 GMT",
      "source": "reuters"
    }
  ],
  "fetchedAt": "2024-01-17T00:00:00Z",
  "fromCache": false
}
```

---

### 6. 시스템 모니터링 API

#### 6.1 시스템 상태 조회

**GET** `/api/system/status`

실시간 서버 시스템 상태를 반환합니다.

**응답:**
```json
{
  "cpuUsage": 25.5,
  "memoryUsagePercent": 45.2,
  "memoryUsed": 2048000000,
  "memoryTotal": 4096000000,
  "heapUsagePercent": 60.3,
  "heapUsed": 512000000,
  "heapMax": 1024000000,
  "threadCount": 45,
  "gcCount": 120,
  "gcTime": 5000,
  "uptimeMillis": 3600000,
  "diskUsage": {
    "total": 500000000000,
    "used": 250000000000,
    "free": 250000000000,
    "percent": 50.0
  }
}
```

#### 6.2 시스템 히스토리 조회

**GET** `/api/system/history?period={period}`

**파라미터:**
- `period` (선택, 기본값: `1h`): 기간 (`1h`, `24h`, `7d`)

**응답:** `SystemHistoryDto` 배열

#### 6.3 최근 시스템 히스토리 조회

**GET** `/api/system/history/recent?count={count}`

**파라미터:**
- `count` (선택, 기본값: 60): 조회할 레코드 수

---

### 7. 생활정보 API

#### 7.1 환율 정보 조회

**GET** `/api/info/exchange?base={base}`

**파라미터:**
- `base` (선택, 기본값: `USD`): 기준 통화

**응답:**
```json
{
  "base": "USD",
  "rates": {
    "KRW": 1300.0,
    "EUR": 0.92,
    "JPY": 150.0
  },
  "date": "2024-01-17"
}
```

#### 7.2 미세먼지 정보 조회

**GET** `/api/info/air-quality?location={location}`

**파라미터:**
- `location` (선택, 기본값: `Seoul`): 지역명

**응답:**
```json
{
  "location": "Seoul",
  "pm10": 45,
  "pm25": 25,
  "grade": "보통",
  "updatedAt": "2024-01-17T00:00:00Z"
}
```

#### 7.3 일출/일몰 시간 조회

**GET** `/api/info/sun-times?lat={lat}&lon={lon}&location={location}`

**파라미터:**
- `lat` (선택, 기본값: `37.5665`): 위도
- `lon` (선택, 기본값: `126.9780`): 경도
- `location` (선택, 기본값: `Seoul`): 지역명

**응답:**
```json
{
  "location": "Seoul",
  "lat": 37.5665,
  "lon": 126.9780,
  "sunrise": "2024-01-17T00:30:00Z",
  "sunset": "2024-01-17T09:15:00Z",
  "dayLength": 31440
}
```

#### 7.4 공휴일 정보 조회

**GET** `/api/info/holidays?year={year}&month={month}`

**파라미터:**
- `year` (선택): 연도 (기본값: 현재 연도)
- `month` (선택): 월 (1-12)

**응답:**
```json
{
  "year": 2024,
  "holidays": [
    {
      "date": "2024-01-01",
      "name": "신정",
      "isHoliday": true
    }
  ]
}
```

#### 7.5 오늘 공휴일 확인

**GET** `/api/info/holidays/today`

**응답:**
```json
{
  "today": "2024-01-17",
  "isHoliday": false,
  "nextHoliday": {
    "date": "2024-02-10",
    "name": "설날"
  }
}
```

#### 7.6 종합 생활정보 조회

**GET** `/api/info/summary?location={location}&lat={lat}&lon={lon}`

환율, 미세먼지, 일출/일몰, 공휴일 정보를 한 번에 조회합니다.

---

### 8. 할 일 관리 API

모든 엔드포인트는 **인증이 필요**합니다 (`X-User-Id` 헤더 또는 쿠키).

#### 8.1 할 일 목록 조회

**GET** `/api/todos?filter={filter}`

**헤더:**
- `X-User-Id`: 사용자 ID (필수)

**파라미터:**
- `filter` (선택): 필터 (`pending`, `completed`, 또는 생략 시 전체)

**응답:**
```json
[
  {
    "id": 1,
    "title": "할 일 제목",
    "description": "설명",
    "completed": false,
    "createdAt": "2024-01-17T00:00:00Z",
    "updatedAt": "2024-01-17T00:00:00Z"
  }
]
```

#### 8.2 미완료 개수 조회

**GET** `/api/todos/count`

**응답:**
```json
{
  "pending": 5
}
```

#### 8.3 할 일 생성

**POST** `/api/todos`

**요청 본문:**
```json
{
  "title": "새 할 일",
  "description": "설명 (선택)"
}
```

#### 8.4 할 일 수정

**PUT** `/api/todos/{todoId}`

**요청 본문:**
```json
{
  "title": "수정된 제목",
  "description": "수정된 설명"
}
```

#### 8.5 할 일 완료 토글

**PATCH** `/api/todos/{todoId}/toggle`

완료/미완료 상태를 토글합니다.

#### 8.6 할 일 삭제

**DELETE** `/api/todos/{todoId}`

#### 8.7 완료된 할 일 모두 삭제

**DELETE** `/api/todos/completed`

---

### 9. 타이머/포모도로 API

모든 엔드포인트는 **인증이 필요**합니다.

#### 9.1 타이머 조회

**GET** `/api/timer/{type}`

**파라미터:**
- `type`: 타이머 타입 (예: `work`, `break`, `pomodoro`)

**응답:**
```json
{
  "id": 1,
  "type": "work",
  "durationSeconds": 1500,
  "remainingSeconds": 1200,
  "isRunning": true,
  "isPaused": false,
  "startedAt": "2024-01-17T00:00:00Z",
  "createdAt": "2024-01-17T00:00:00Z"
}
```

#### 9.2 타이머 생성/초기화

**POST** `/api/timer/{type}`

**요청 본문:**
```json
{
  "durationSeconds": 300
}
```

#### 9.3 타이머 시작

**POST** `/api/timer/{type}/start`

#### 9.4 타이머 일시정지

**POST** `/api/timer/{type}/pause`

#### 9.5 타이머 정지/리셋

**POST** `/api/timer/{type}/stop`

#### 9.6 포모도로 초기화

**POST** `/api/timer/pomodoro/init`

포모도로 타이머를 생성합니다 (기본: 작업 25분, 휴식 5분).

#### 9.7 포모도로 완료

**POST** `/api/timer/pomodoro/complete`

현재 세션을 완료 처리합니다.

#### 9.8 다음 포모도로 시작

**POST** `/api/timer/pomodoro/next`

다음 세션(작업 또는 휴식)을 시작합니다.

---

### 10. 알림 API

모든 엔드포인트는 **인증이 필요**합니다.

#### 10.1 알림 규칙 목록 조회

**GET** `/api/alerts/rules`

**응답:**
```json
[
  {
    "id": 1,
    "type": "stock_price",
    "target": "AAPL",
    "conditionType": "above",
    "threshold": 150.0,
    "enabled": true,
    "activeFrom": "09:00",
    "activeTo": "18:00",
    "activeDays": "MON,TUE,WED,THU,FRI",
    "channels": "sse,browser",
    "createdAt": "2026-09-16T00:00:00Z",
    "updatedAt": "2026-09-16T00:00:00Z"
  }
]
```

> ⚠️ 이 문서는 예전에 `name`/`condition` 같은 **존재하지 않는 필드**를 적고 있었습니다.
> 위 형태가 실제 `AlertRuleDto` 입니다.

**규칙 타입(`type`)**

| type | target | 값 |
|---|---|---|
| `stock_price` / `stock_change` / `stock_percent` | 종목 심볼 | 현재가 / 변동금액 / 변동률 |
| `cpu` / `memory` / `heap` | — | 사용률(%) |
| `weather_temp` / `weather_humidity` | 도시명 | 온도(°C) / 습도(%) |
| `exchange_rate` | `USD/KRW` 또는 `KRW` | 환율 |
| `air_pm10` / `air_pm25` / `air_aqi` | 지역명(기본 `서울`) | 농도 / 통합지수 |
| `air_grade` | 지역명 | **통합** 등급 순위(1 좋음 · 2 보통 · 3 나쁨 · 4 매우나쁨) |
| `air_pm10_grade` / `air_pm25_grade` | 지역명 | 항목별 등급 순위 |

**조건(`conditionType`)**: `above`(초과) · `below`(미만) · `equals`(도달) · `at_least`(이상) · `at_most`(이하)

- 주식·날씨·시스템 알림은 대시보드 요청이 들어올 때 함께 검사됩니다.
- **환율·미세먼지는 주기 작업**(기본 10분, `myapi.alerts.lifeinfo.interval-ms`)이 검사합니다.
  해당 규칙이 **하나도 없으면 외부 API 를 부르지 않습니다**.
- ⚠️ `air_grade`(통합 등급)와 `air_pm10_grade`(PM10 등급)는 **같지 않습니다**. PM10 120 은
  PM10 등급으로는 "나쁨" 이지만 통합 등급으로는 "보통"(AQI 96)입니다.

**시간대 조건**

`activeFrom`/`activeTo` 는 `HH:mm`, 구간은 `[from, to)` 입니다. 기준 시간대는 **Asia/Seoul** 고정.

- 비워 두면 시간 제한 없음
- `22:00`~`06:00` 처럼 from &gt; to 이면 자정을 넘는 구간
- from == to 이면 24시간
- `activeDays` 는 `MON,TUE,...`(한글 `월,화` 도 가능), 비우면 매일
- 표기를 못 읽으면 **시간 제한 없이 발동**하고 `warn` 로그를 남깁니다(오타 하나로 알림이 영영 안
  오는 쪽이 더 나쁘다고 봤습니다)

**알림 채널(`channels`)**

| 값 | 동작 |
|---|---|
| `sse` (기본) | SSE 스트림 → 화면 토스트 |
| `sse,browser` | 위 + 브라우저 OS 데스크톱 알림(Web Notifications) |

- 서버는 채널과 무관하게 항상 SSE 로 내려보냅니다. `browser` 는 화면이 데스크톱 알림까지
  띄우라는 힌트입니다(권한이 없으면 토스트만 뜹니다).
- 기존 규칙(값 없음)은 `sse` 로 취급합니다 — 쓰던 규칙이 갑자기 OS 알림을 띄우지 않게.
- 이메일 채널은 **없습니다**. 발송 경로(SMTP/SES)가 이 저장소에 없어서, 목록에만 넣어 두면
  "켰는데 안 온다" 가 됩니다.

#### 10.2 알림 규칙 생성

**POST** `/api/alerts/rules`

**요청 본문:**
```json
{
  "type": "exchange_rate",
  "target": "USD/KRW",
  "conditionType": "above",
  "threshold": 1400.0,
  "enabled": true,
  "activeFrom": "09:00",
  "activeTo": "18:00",
  "activeDays": "MON,TUE,WED,THU,FRI",
  "channels": "sse,browser"
}
```

미세먼지 "나쁨 이상" 규칙:
```json
{
  "type": "air_grade",
  "target": "서울",
  "conditionType": "at_least",
  "threshold": 3,
  "channels": "sse,browser"
}
```

#### 10.3 알림 규칙 수정

**PUT** `/api/alerts/rules/{ruleId}`

#### 10.4 알림 규칙 삭제

**DELETE** `/api/alerts/rules/{ruleId}`

#### 10.5 알림 규칙 활성화/비활성화

**PATCH** `/api/alerts/rules/{ruleId}/toggle`

**요청 본문:**
```json
{
  "enabled": true
}
```

#### 10.6 알림 로그 조회

**GET** `/api/alerts/logs?page={page}&size={size}`

**파라미터:**
- `page` (선택, 기본값: 0): 페이지 번호
- `size` (선택, 기본값: 20): 페이지 크기

#### 10.7 미읽음 알림 조회

**GET** `/api/alerts/logs/unread`

#### 10.8 미읽음 알림 개수 조회

**GET** `/api/alerts/logs/unread/count`

**응답:**
```json
{
  "count": 5
}
```

#### 10.9 알림 읽음 처리

**POST** `/api/alerts/logs/{logId}/read`

#### 10.10 모든 알림 읽음 처리

**POST** `/api/alerts/logs/read-all`

#### 10.11 알림 SSE 스트림

**GET** `/api/alerts/stream`

실시간 알림을 SSE로 수신합니다.

---

### 11. 사용자 설정 API

모든 엔드포인트는 **인증이 필요**합니다.

#### 11.1 설정 조회

**GET** `/api/settings`

**응답:**
```json
{
  "theme": "dark",
  "language": "ko",
  "notifications": {
    "enabled": true,
    "sound": true
  },
  "dashboard": {
    "layout": "grid",
    "widgets": ["stocks", "weather", "news"]
  }
}
```

#### 11.2 설정 저장

**PUT** `/api/settings`

**요청 본문:** 전체 설정 객체

#### 11.3 섹션별 설정 업데이트

**PATCH** `/api/settings/{section}`

**파라미터:**
- `section`: 섹션명 (예: `theme`, `notifications`, `dashboard`)

**요청 본문:**
```json
{
  "enabled": true,
  "sound": false
}
```

#### 11.4 설정 초기화

**DELETE** `/api/settings`

기본 설정으로 초기화합니다.

---


### 12. 소셜/실시간 API (`/api/social`)

> 🔴 이 절은 2026-09-16 에 추가됐다. `SocialController` 는 오래전부터 있었는데
> **README 에 한 줄도 없었다** — 문서만 읽은 사람에게는 **존재하지 않는 기능**이었다.

뉴스·교통·긴급재난을 JSON 과 **SSE 스트림** 두 형태로 낸다.

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/social/news` | 뉴스 목록 (JSON) |
| GET | `/api/social/traffic` | 교통 정보 (JSON) |
| GET | `/api/social/emergency` | 긴급재난 (JSON) |
| GET | `/api/social/traffic/raw` | 교통 원본 응답 |
| GET | `/api/social/emergency/raw` | 긴급재난 원본 응답 |
| GET | `/api/social/news/stream` | 뉴스 **SSE** (`text/event-stream`) |
| GET | `/api/social/traffic/stream` | 교통 **SSE** |
| GET | `/api/social/emergency/stream` | 긴급재난 **SSE** |

⚠️ `/traffic`·`/emergency` 는 `TRAFFIC_API_KEY`·`EMERGENCY_API_SERVICE_KEY` 가 필요하다.
키가 없으면 **빈 결과가 나오지 예외가 나지 않는다** — "데이터가 없다" 로 오인하기 쉽다.

---

### 13. AI 리포트 API (`/api/ai-report`)

> 🔴 이 절도 2026-09-16 에 추가됐다(위와 같은 이유).

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/ai-report/topics` | 리포트 토픽 목록 |
| POST | `/api/ai-report/generate` | 리포트 생성 |
| GET | `/api/ai-report/history?type=&page=&size=` | 리포트 이력 목록(발췌만) |
| GET | `/api/ai-report/history/{id}` | 리포트 이력 상세(전문) |

LLM 호출은 **`osh-ai-gateway` 경유**가 표준이다(`GEMINI_GATEWAY_BASE_URL`).
게이트웨이 토큰이 없으면 생성이 실패한다.

#### 자동 리포트 (스케줄러)

| 무엇 | 언제 | 설정 |
|---|---|---|
| 일간 자동 리포트 | 매일 20:00 | `myapi.report.daily.cron` |
| 주간 트렌드 분석 | 일요일 20:30 | `myapi.report.weekly.cron` |

🔴 **대상 사용자는 `myapi.report.daily.users`(쉼표 구분)에 적은 사람만이고, 기본값은 비어 있다**
(=아무 일도 하지 않는다). 모든 사용자를 훑도록 만들면 익명 프로필 수만큼 Gemini 를 부르게 된다
— 「동시성 한계」 절에 적힌 736만 행 사고와 같은 모양이다. 대상이 비어 있다는 사실도 로그로
남기므로, 켠 줄 알고 기다리는 일은 없다.

주간 트렌드는 최근 7일 안의 **일간 리포트가 2건 이상**일 때만 만든다. 비교 대상이 없는데
"트렌드" 를 시키면 모델이 지어낸다.

#### 리포트 이력 보관 정책

| 상한 | 기본값 | 강제 시점 | 설정 |
|---|---|---|---|
| 사용자당 개수 | 50건 | **저장할 때마다** | `myapi.report.history.max-per-user` |
| 보관 기간 | 90일 | 매일 03:00 | `myapi.report.history.retention-days` |

- 개수 상한을 저장 경로에 둔 이유: 주기 작업이 꺼져 있어도 **이 상한만은 반드시 지켜져야** 한다.
  비용은 인덱스를 탄 `COUNT` 한 번이고, 삭제는 넘쳤을 때만(보통 한 건) 일어난다.
- 화면에서 만든 리포트도 이력에 남는다(`myapi.report.history.save-manual`, 기본 `true`).
  요청 경로에 `INSERT` 한 번이 늘어나므로 끄고 싶으면 `false`. 익명 요청마다 쌓이던 프로필과
  달리 **사용자가 명시적으로 누른 한 번**에만 일어난다는 점이 다르다.
- 목록 조회는 본문을 싣지 않는다(발췌 300자). 커넥션이 하나라 전문 20건을 끌어오면 그대로
  다른 요청의 지연이 된다.

---

## 인증 및 사용자 식별

대부분의 API는 사용자별 데이터를 제공하기 위해 사용자 식별이 필요합니다.

### 방법 1: HTTP 헤더 사용

```bash
curl -H "X-User-Id: user123" "http://localhost:8080/api/todos"
```

### 방법 2: 쿠키 사용

브라우저에서는 자동으로 쿠키가 설정됩니다 (`UserIdentificationFilter`).

### 방법 3: JavaScript 예시

```javascript
fetch('http://localhost:8080/api/todos', {
  headers: {
    'X-User-Id': 'user123',
    'Content-Type': 'application/json'
  }
})
```

**인증이 필요한 API:**
- 할 일 관리 API (`/api/todos/*`)
- 타이머 API (`/api/timer/*`)
- 알림 API (`/api/alerts/*`)
- 설정 API (`/api/settings/*`)

**인증이 불필요한 API:**
- 주식 API (`/api/finnhub/*`)
- 날씨 API (`/api/weather/*`)
- RSS API (`/api/rss/*`)
- 시스템 API (`/api/system/*`)
- 생활정보 API (`/api/info/*`)

---

## 응답 형식

### 성공 응답

모든 성공 응답은 HTTP 상태 코드 `200 OK`와 함께 JSON 형식으로 반환됩니다.

### 에러 응답

에러 발생 시 다음 형식으로 반환됩니다:

```json
{
  "timestamp": "2024-01-17T00:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "사용자 ID가 필요합니다",
  "path": "/api/todos"
}
```

**일반적인 HTTP 상태 코드:**
- `200 OK`: 성공
- `400 Bad Request`: 잘못된 요청
- `401 Unauthorized`: 인증 필요
- `404 Not Found`: 리소스를 찾을 수 없음
- `500 Internal Server Error`: 서버 오류

---

## 에러 처리

API는 전역 예외 처리기(`GlobalExceptionHandler`)를 통해 일관된 에러 응답을 제공합니다.

**예시:**
```bash
# 사용자 ID 누락
curl "http://localhost:8080/api/todos"
# 응답: 400 Bad Request

# 존재하지 않는 리소스
curl "http://localhost:8080/api/todos/999"
# 응답: 404 Not Found
```

---

## 캐싱 전략

| 데이터 타입 | TTL | 갱신 방식 |
|------------|-----|-----------|
| 주식 시세 | 60초 | 요청 시 만료 후 갱신 |
| 날씨 정보 | 60초 | 백그라운드 스케줄러 |
| 위치 날씨 | 60초 | 백그라운드 스케줄러 |
| RSS 피드 | 10분 | 요청 시 만료 후 갱신 |
| 시스템 상태 | 실시간 | 매 요청마다 갱신 |
| 생활정보 | 다양 | API별 상이 |

---

## 프로젝트 구조

```
myapi/
├── build.sh              # 빌드 스크립트
├── run.sh                # 실행 스크립트 (start/stop/restart)
├── dashboard.sh           # 터미널 대시보드
├── pom.xml                # Maven 설정
├── README.md              # 이 문서
└── src/main/
    ├── java/com/example/myapi/
    │   ├── config/        # 설정 클래스
    │   │   ├── AirKoreaProperties.java
    │   │   ├── DatabaseConfig.java
    │   │   ├── FinnhubProperties.java
    │   │   ├── HttpClientConfig.java
    │   │   ├── OpenWeatherProperties.java
    │   │   └── WebConfig.java
    │   ├── controller/    # REST 컨트롤러
    │   │   ├── AlertController.java
    │   │   ├── DashboardController.java
    │   │   ├── FinnhubController.java
    │   │   ├── InfoController.java
    │   │   ├── LocationController.java
    │   │   ├── RssController.java
    │   │   ├── SettingsController.java
    │   │   ├── SystemController.java
    │   │   ├── TimerController.java
    │   │   ├── TodoController.java
    │   │   └── WeatherController.java
    │   ├── dto/           # 데이터 전송 객체
    │   │   ├── alert/
    │   │   ├── dashboard/
    │   │   ├── finnhub/
    │   │   ├── info/
    │   │   ├── location/
    │   │   ├── productivity/
    │   │   ├── rss/
    │   │   ├── settings/
    │   │   ├── system/
    │   │   └── weather/
    │   ├── entity/        # JPA 엔티티
    │   │   ├── AlertLog.java
    │   │   ├── AlertRule.java
    │   │   ├── SystemHistory.java
    │   │   ├── Timer.java
    │   │   ├── Todo.java
    │   │   ├── UserProfile.java
    │   │   └── UserSettings.java
    │   ├── exception/     # 예외 처리
    │   │   └── GlobalExceptionHandler.java
    │   ├── filter/        # 필터
    │   │   └── UserIdentificationFilter.java
    │   ├── repository/    # JPA 리포지토리
    │   │   ├── AlertLogRepository.java
    │   │   ├── AlertRuleRepository.java
    │   │   ├── SystemHistoryRepository.java
    │   │   ├── TimerRepository.java
    │   │   ├── TodoRepository.java
    │   │   ├── UserProfileRepository.java
    │   │   └── UserSettingsRepository.java
    │   ├── service/       # 비즈니스 로직
    │   │   ├── AlertIntegrationService.java
    │   │   ├── AlertService.java
    │   │   ├── DashboardService.java
    │   │   ├── FinnhubService.java
    │   │   ├── LifeInfoService.java
    │   │   ├── LocationWeatherService.java
    │   │   ├── RssService.java
    │   │   ├── SystemHistoryService.java
    │   │   ├── SystemStatusService.java
    │   │   ├── TimerService.java
    │   │   ├── TodoService.java
    │   │   ├── UserSettingsService.java
    │   │   └── WeatherService.java
    │   └── MyApiApplication.java
    └── resources/
        ├── application.yml # Spring Boot 설정
        └── static/        # 🔴 빌드 산출물 — 소스가 아니다, 커밋하지 않는다
                            #    frontend/dist 가 mvn package 때 여기로 복사된다

frontend/                   # Vue 3 + Vite + Pinia (2026-09-26 개편)
├── src/
│   ├── api/client.ts      # 네트워크 호출 단일 창구
│   ├── stores/            # dashboard.ts(SSE+즉시갱신) · ui.ts(모드·토스트)
│   ├── sections/registry.ts  # 섹션↔모드 매핑 — 여기 하나만 보면 된다
│   ├── sections/{social,finance,life,productivity}/  # 16개 섹션
│   ├── modals/            # SettingsModal · AiReportModal
│   └── components/        # DashboardSection · ModalShell · MediaPlayer
├── vite.config.ts         # base:'/myapi/' 고정 — 바꾸면 에셋 전부 404
└── package.json
```

---

## 데이터베이스

SQLite 데이터베이스를 사용하며, 파일은 `./data/dashboard.db`에 저장됩니다.

**주요 테이블:**
- `user_profile`: 사용자 프로필
- `user_settings`: 사용자 설정
- `todo`: 할 일 목록
- `timer`: 타이머 정보
- `alert_rule`: 알림 규칙
- `alert_log`: 알림 로그
- `system_history`: 시스템 히스토리

### ⚠️ 동시성 한계 — 모든 DB 접근이 **직렬화**된다

설정(`application.yml`)이 이렇게 돼 있다:

```yaml
url: jdbc:sqlite:./data/dashboard.db?busy_timeout=30000&journal_mode=WAL
hikari:
  maximum-pool-size: 1          # ← 연결이 하나뿐이다
  connection-timeout: 30000
connection-init-sql: "PRAGMA journal_mode=WAL; PRAGMA busy_timeout=30000;
                      PRAGMA synchronous=NORMAL; PRAGMA cache_size=10000; ..."
```

🔴 **풀 크기가 1이므로 쓰기뿐 아니라 읽기까지 한 줄로 선다.** SQLite 자체는 WAL 모드에서
*"쓰는 중에도 읽을 수 있다"* 를 주지만, **연결이 하나면 그 이점이 나오지 않는다** —
동시 요청은 커넥션을 기다린다. 즉 여기서 `journal_mode=WAL` 은 동시성이 아니라
**쓰기 지연과 `SQLITE_BUSY` 회피**를 위한 설정이다.

관측된 값(2026-09-16, `.25` 운영):

| | |
|---|---|
| `dashboard.db` | **772 MB** |
| `-wal` | 4 MB |
| 커넥션 풀 | **1** (뉴스 DB 는 별개로 5) |
| `busy_timeout` | 30초 — 이 시간을 넘기면 요청이 실패한다 |
| `connection-timeout` | 30초 — 커넥션을 못 얻어도 30초까지 기다린다 |

**⇒ 최악의 경우 한 요청이 최대 30초까지 대기할 수 있다.** 호출자(대시보드 프런트,
HARU 도구 등)의 타임아웃이 그보다 짧으면 **이쪽은 아직 일하는 중인데 저쪽이 먼저 끊는다.**

**언제 문제가 되나**
- 대량 쓰기(예: `system_history` 적재)가 도는 동안 읽기 요청이 밀린다
- DB 가 커질수록 개별 쿼리가 느려지고 그만큼 줄이 길어진다 — 현재 **772MB**

**지금은 왜 괜찮은가**
- 개인용이라 동시 사용자가 사실상 1명이고, 관측된 지연이 문제가 된 적이 없다
- `synchronous=NORMAL` + WAL 로 쓰기 자체는 빠르다

**넘어야 할 선**
- 동시 사용자가 늘거나 · `dashboard.db` 가 계속 커지거나 · 30초 대기가 실제로 관측되면
  → 풀 크기를 늘리기 전에 **PostgreSQL 이전**을 봐야 한다. SQLite 에서 풀만 늘리면
  `SQLITE_BUSY` 가 늘 뿐이다(그래서 1로 둔 것이다).

---

## 트러블슈팅

### API 키 오류

```
Could not resolve placeholder 'FINNHUB_API_KEY'
```

**해결:**
```bash
source /etc/myapi/conf
```

### 포트 충돌

```
Port 8080 was already in use
```

**해결:**
```bash
kill $(lsof -ti:8080)
```

### SQLite 잠금 오류

SQLite는 기본적으로 단일 연결을 권장합니다. 설정에서 `maximum-pool-size: 1`로 설정되어 있습니다.

---

## 라이선스

MIT License

---

## 연락처 및 지원

문제가 발생하거나 기능 요청이 있으시면 이슈를 등록해주세요.
