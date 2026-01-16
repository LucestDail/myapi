# MyAPI - 주식 & 날씨 정보 API 서버

Spring Boot 기반의 REST API 서버로, 주식 정보(Finnhub)와 한국 주요 도시 날씨 정보(OpenWeatherMap)를 제공합니다.

## 기술 스택

- **Java 17**
- **Spring Boot 3.3.6**
- **Maven**

---

## Ubuntu 24.04 서버 설치 가이드

### 1. 시스템 업데이트

```bash
sudo apt update && sudo apt upgrade -y
```

### 2. Java 17 설치

```bash
# OpenJDK 17 설치
sudo apt install openjdk-17-jdk -y

# 설치 확인
java -version
# openjdk version "17.x.x" 출력 확인
```

### 3. Maven 설치

```bash
# Maven 설치
sudo apt install maven -y

# 설치 확인
mvn -version
```

### 4. Git 설치 (선택)

```bash
sudo apt install git -y
```

### 5. 방화벽 설정 (선택)

```bash
# 8080 포트 오픈
sudo ufw allow 8080/tcp
sudo ufw reload
```

---

## API 키 설정

프로젝트 빌드 및 실행을 위해 API 키를 서버에 설정해야 합니다.

### API 키 파일 생성

```bash
# 디렉토리 생성
sudo mkdir -p /etc/myapi

# API 키 파일 생성
sudo tee /etc/myapi/api-keys.conf > /dev/null << 'EOF'
# MyAPI Configuration
# Finnhub API Key (https://finnhub.io/)
export FINNHUB_API_KEY="여기에_FINNHUB_API_키_입력"

# OpenWeatherMap API Key (https://openweathermap.org/api)
export OPENWEATHER_API_KEY="여기에_OPENWEATHER_API_키_입력"
EOF

# 파일 권한 설정 (보안)
sudo chmod 600 /etc/myapi/api-keys.conf
```

### API 키 발급 링크

| 서비스 | 발급 링크 | 비고 |
|--------|----------|------|
| Finnhub | https://finnhub.io/register | Free Tier: 30 calls/sec |
| OpenWeatherMap | https://openweathermap.org/api | Free Plan: 60 calls/min |

---

## 빌드 및 실행

### 원스텝 빌드

```bash
# 실행 권한 부여
chmod +x build.sh run.sh

# 빌드 (API 키 확인 + Maven 빌드)
./build.sh
```

### 서버 실행

```bash
# JAR 파일로 실행
./run.sh

# 또는 직접 실행
source /etc/myapi/api-keys.conf
java -jar target/myapi-0.0.1-SNAPSHOT.jar

# 또는 Maven으로 실행
source /etc/myapi/api-keys.conf
mvn spring-boot:run
```

### 백그라운드 실행 (Production)

```bash
# nohup으로 백그라운드 실행
source /etc/myapi/api-keys.conf
nohup java -jar target/myapi-0.0.1-SNAPSHOT.jar > myapi.log 2>&1 &

# 로그 확인
tail -f myapi.log

# 프로세스 확인
ps aux | grep myapi

# 종료
kill $(pgrep -f myapi-0.0.1-SNAPSHOT.jar)
```

서버는 `http://localhost:8080`에서 실행됩니다.

---

## 프로젝트 구조

```
myapi/
├── build.sh                         # 빌드 스크립트
├── run.sh                           # 실행 스크립트
├── pom.xml                          # Maven 설정
├── README.md
└── src/main/
    ├── java/com/example/myapi/
    │   ├── config/                  # 설정 클래스
    │   │   ├── HttpClientConfig.java
    │   │   ├── FinnhubProperties.java
    │   │   └── OpenWeatherProperties.java
    │   ├── controller/              # REST 컨트롤러
    │   │   ├── FinnhubController.java
    │   │   └── WeatherController.java
    │   ├── service/                 # 비즈니스 로직
    │   │   ├── FinnhubService.java
    │   │   └── WeatherService.java
    │   ├── dto/                     # Data Transfer Objects
    │   │   ├── finnhub/
    │   │   └── weather/
    │   ├── exception/               # 예외 처리
    │   │   └── GlobalExceptionHandler.java
    │   └── MyApiApplication.java
    └── resources/
        └── application.yml          # 앱 설정 (API 키는 환경변수)
```

---

## API 문서

### 1. 주식 정보 API (Finnhub)

[Finnhub API](https://finnhub.io/docs/api) 기반의 주식 정보를 제공합니다.

| 메서드 | 엔드포인트 | 설명 |
|--------|-----------|------|
| `GET` | `/api/finnhub/quote?symbol={symbol}` | 실시간 주가 |
| `GET` | `/api/finnhub/profile?symbol={symbol}` | 회사 프로필 |
| `GET` | `/api/finnhub/news?category={category}` | 마켓 뉴스 |
| `GET` | `/api/finnhub/company-news?symbol={symbol}&from={date}&to={date}` | 회사 뉴스 |
| `GET` | `/api/finnhub/recommendation?symbol={symbol}` | 애널리스트 추천 |
| `GET` | `/api/finnhub/financials?symbol={symbol}` | 재무 지표 |
| `GET` | `/api/finnhub/peers?symbol={symbol}` | 동종 기업 |

#### 예시

**실시간 주가 조회**
```bash
curl "http://localhost:8080/api/finnhub/quote?symbol=AAPL"
```

응답:
```json
{
  "currentPrice": 258.21,
  "change": -1.75,
  "percentChange": -0.6732,
  "highPrice": 261.04,
  "lowPrice": 257.05,
  "openPrice": 260.65,
  "previousClose": 259.96,
  "timestamp": 1768510800
}
```

**회사 프로필 조회**
```bash
curl "http://localhost:8080/api/finnhub/profile?symbol=AAPL"
```

응답:
```json
{
  "country": "US",
  "currency": "USD",
  "exchange": "NASDAQ NMS - GLOBAL MARKET",
  "finnhubIndustry": "Technology",
  "ipo": "1980-12-12",
  "logo": "https://static2.finnhub.io/file/publicdatany/finnhubimage/stock_logo/AAPL.png",
  "marketCapitalization": 3815401.86,
  "name": "Apple Inc",
  "phone": "14089961010",
  "shareOutstanding": 14776.35,
  "ticker": "AAPL",
  "weburl": "https://www.apple.com/"
}
```

---

### 2. 날씨 정보 API (OpenWeatherMap)

[OpenWeatherMap API](https://openweathermap.org/api) 기반으로 한국 주요 10개 도시의 날씨 정보를 제공합니다.

#### 특징
- **자동 캐싱**: 서버 시작 시 모든 도시 날씨 데이터 로드
- **1분 주기 자동 갱신**: 스케줄러로 백그라운드 업데이트
- **캐시 데이터만 반환**: 사용자 요청 시 외부 API 직접 호출 없음

#### 지원 도시

| 영문 | 한글 |
|------|------|
| Seoul | 서울 |
| Busan | 부산 |
| Incheon | 인천 |
| Daegu | 대구 |
| Daejeon | 대전 |
| Gwangju | 광주 |
| Suwon | 수원 |
| Ulsan | 울산 |
| Jeju | 제주 |
| Changwon | 창원 |

#### 엔드포인트

| 메서드 | 엔드포인트 | 설명 |
|--------|-----------|------|
| `GET` | `/api/weather` | 전체 도시 날씨 |
| `GET` | `/api/weather/{city}` | 특정 도시 날씨 |
| `GET` | `/api/weather/cities/list` | 지원 도시 목록 |
| `GET` | `/api/weather/cache/status` | 캐시 상태 |

#### 예시

**전체 도시 날씨 조회**
```bash
curl "http://localhost:8080/api/weather"
```

**특정 도시 날씨 조회 (서울)**
```bash
curl "http://localhost:8080/api/weather/seoul"
```

응답:
```json
{
  "city": "Seoul",
  "cityKo": "서울",
  "country": "KR",
  "lat": 37.5665,
  "lon": 126.978,
  "weather": "Mist",
  "description": "mist",
  "icon": "50n",
  "temperature": 274.91,
  "temperatureCelsius": 1.76,
  "feelsLike": 272.67,
  "feelsLikeCelsius": -0.48,
  "humidity": 93,
  "pressure": 1019,
  "windSpeed": 2.06,
  "windDeg": 350,
  "cloudiness": 75,
  "visibility": 2500,
  "sunrise": "2026-01-15T22:45:31Z",
  "sunset": "2026-01-16T08:37:23Z",
  "fetchedAt": "2026-01-16T13:56:29.394560Z"
}
```

**캐시 상태 확인**
```bash
curl "http://localhost:8080/api/weather/cache/status"
```

응답:
```json
{
  "lastUpdated": "2026-01-16T13:56:40.483063Z",
  "totalCities": 10,
  "cachedCities": 10,
  "availableCities": ["Seoul", "Busan", "Incheon", "Daegu", "Daejeon", "Gwangju", "Suwon", "Ulsan", "Jeju", "Changwon"]
}
```

---

## Troubleshooting

### API 키 오류
```
Caused by: java.lang.IllegalArgumentException: Could not resolve placeholder 'FINNHUB_API_KEY'
```
→ `/etc/myapi/api-keys.conf` 파일이 없거나 환경변수가 로드되지 않음. `source /etc/myapi/api-keys.conf` 실행 후 재시도.

### 포트 충돌
```
Web server failed to start. Port 8080 was already in use.
```
→ 기존 프로세스 종료: `kill $(lsof -ti:8080)`

### Maven 빌드 실패
```
[ERROR] COMPILATION ERROR
```
→ Java 17 설치 확인: `java -version`

---

## 라이선스

MIT License
