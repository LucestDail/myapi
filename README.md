# MyAPI - 백엔드 API 서버

Spring Boot 기반의 REST API 서버로, 주식 정보, 날씨, 뉴스, 시스템 모니터링, 생산성 도구(할 일, 타이머), 알림 등 다양한 기능을 제공합니다.

이 API는 웹 프론트엔드, iOS/Android 모바일 앱에서 사용할 수 있도록 설계되었습니다.

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
- [인증 및 사용자 식별](#인증-및-사용자-식별)
- [응답 형식](#응답-형식)
- [에러 처리](#에러-처리)
- [캐싱 전략](#캐싱-전략)
- [프로젝트 구조](#프로젝트-구조)

---

## 기술 스택

- **Java 17**
- **Spring Boot 3.3.6**
- **Maven**
- **SQLite** (데이터베이스)
- **JPA/Hibernate**
- **Server-Sent Events (SSE)** (실시간 스트리밍)

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

```bash
# 설정 디렉토리 생성
sudo mkdir -p /etc/myapi

# API 키 파일 생성
sudo tee /etc/myapi/api-keys.conf > /dev/null << 'EOF'
export FINNHUB_API_KEY="your_finnhub_key"
export OPENWEATHER_API_KEY="your_openweather_key"
export AIRKOREA_API_KEY="your_airkorea_key"
EOF

# 파일 보안 설정
sudo chmod 600 /etc/myapi/api-keys.conf

# 환경 변수 로드
source /etc/myapi/api-keys.conf
```

또는 실행 스크립트(`run.sh`)에서 자동으로 로드됩니다.

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
    "name": "주가 상승 알림",
    "type": "STOCK",
    "condition": "AAPL > 150",
    "enabled": true,
    "createdAt": "2024-01-17T00:00:00Z"
  }
]
```

#### 10.2 알림 규칙 생성

**POST** `/api/alerts/rules`

**요청 본문:**
```json
{
  "name": "주가 상승 알림",
  "type": "STOCK",
  "condition": "AAPL > 150",
  "enabled": true
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
        └── static/        # 정적 파일 (웹 UI)
            ├── index.html
            ├── css/
            └── js/
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

---

## 트러블슈팅

### API 키 오류

```
Could not resolve placeholder 'FINNHUB_API_KEY'
```

**해결:**
```bash
source /etc/myapi/api-keys.conf
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
