# MyAPI - Stock, Weather, News & System API Server

Spring Boot REST API server providing stock quotes, weather data, RSS news feeds, and system monitoring.

## Tech Stack

- **Java 17**
- **Spring Boot 3.3.6**
- **Maven**

---

## Ubuntu 24.04 Installation

```bash
# System update
sudo apt update && sudo apt upgrade -y

# Java 17
sudo apt install openjdk-17-jdk -y

# Maven
sudo apt install maven -y

# Git (optional)
sudo apt install git -y

# Firewall (optional)
sudo ufw allow 8080/tcp
```

---

## API Key Setup

```bash
# Create config directory
sudo mkdir -p /etc/myapi

# Create API key file
sudo tee /etc/myapi/api-keys.conf > /dev/null << 'EOF'
export FINNHUB_API_KEY="your_finnhub_key"
export OPENWEATHER_API_KEY="your_openweather_key"
EOF

# Secure the file
sudo chmod 600 /etc/myapi/api-keys.conf
```

| Service | Link | Limit |
|---------|------|-------|
| Finnhub | https://finnhub.io/register | 30 calls/sec |
| OpenWeatherMap | https://openweathermap.org/api | 60 calls/min |

---

## Build & Run

```bash
chmod +x build.sh run.sh dashboard.sh

# Build
./build.sh

# Run (foreground)
./run.sh

# Run (background)
./run.sh start

# Stop
./run.sh stop

# Dashboard
./dashboard.sh
```

---

## API Endpoints

### 1. Stock API (Finnhub)

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/finnhub/quote?symbol=AAPL` | Real-time quote |
| `GET` | `/api/finnhub/profile?symbol=AAPL` | Company profile |
| `GET` | `/api/finnhub/news?category=general` | Market news |
| `GET` | `/api/finnhub/recommendation?symbol=AAPL` | Analyst recommendations |
| `GET` | `/api/finnhub/financials?symbol=AAPL` | Financial metrics |
| `GET` | `/api/finnhub/peers?symbol=AAPL` | Company peers |
| `GET` | `/api/finnhub/cache/status` | Cache status |

**Example:**
```bash
curl "http://localhost:8080/api/finnhub/quote?symbol=AAPL"
```

---

### 2. Weather API (OpenWeatherMap)

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/weather` | All cities weather |
| `GET` | `/api/weather/{city}` | Single city weather |
| `GET` | `/api/weather/cities/list` | Available cities |
| `GET` | `/api/weather/cache/status` | Cache status |

**Supported Cities:** Seoul, Busan, Incheon, Daegu, Daejeon, Gwangju, Suwon, Ulsan, Jeju, Changwon

**Example:**
```bash
curl "http://localhost:8080/api/weather/seoul"
```

---

### 3. Location API (wttr.in)

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/location/weather` | Current location weather (cached 60s) |

**Example:**
```bash
curl "http://localhost:8080/api/location/weather"
```

---

### 4. RSS Feed API

All feeds are cached for **10 minutes** per URL.

#### Yahoo Finance
| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/rss/yahoo/market` | Market News |
| `GET` | `/api/rss/yahoo/stock?symbol=AAPL` | Stock-specific News |

#### Yonhap (Korean News)
| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/rss/yonhap/all` | All News |
| `GET` | `/api/rss/yonhap/economy` | Economy |
| `GET` | `/api/rss/yonhap/politics` | Politics |
| `GET` | `/api/rss/yonhap/it` | IT/Science |

#### Custom & Cache
| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/rss/custom?url=...` | Custom RSS URL |
| `GET` | `/api/rss/cache/status` | Cache status |

**Example:**
```bash
curl "http://localhost:8080/api/rss/reuters/top"
```

**Response:**
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
      "pubDate": "Fri, 17 Jan 2026 00:00:00 GMT",
      "source": "reuters"
    }
  ],
  "fetchedAt": "2026-01-17T00:00:00Z",
  "fromCache": false
}
```

---

### 5. System Status API

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/system/status` | Server system status (real-time) |

**Response includes:** CPU, Memory, Heap, Threads, GC, Uptime, Disk info

**Example:**
```bash
curl "http://localhost:8080/api/system/status"
```

---

## Project Structure

```
myapi/
├── build.sh              # Build script
├── run.sh                # Run script (start/stop/restart)
├── dashboard.sh          # Terminal dashboard
├── pom.xml
├── README.md
└── src/main/java/com/example/myapi/
    ├── config/           # Configuration
    ├── controller/       # REST Controllers
    │   ├── FinnhubController.java
    │   ├── WeatherController.java
    │   ├── LocationController.java
    │   ├── RssController.java
    │   └── SystemController.java
    ├── service/          # Business Logic
    │   ├── FinnhubService.java
    │   ├── WeatherService.java
    │   ├── LocationWeatherService.java
    │   ├── RssService.java
    │   └── SystemStatusService.java
    ├── dto/              # Data Transfer Objects
    │   ├── finnhub/
    │   ├── weather/
    │   ├── location/
    │   ├── rss/
    │   └── system/
    └── exception/        # Exception Handling
```

---

## Caching Strategy

| Data | TTL | Refresh |
|------|-----|---------|
| Stock quotes | 60s | On request after expiry |
| Weather | 60s | Background scheduler |
| Location | 60s | Background scheduler |
| RSS feeds | **10 min** | On request after expiry |
| System status | Real-time | Every request |

---

## Troubleshooting

**API Key Error:**
```
Could not resolve placeholder 'FINNHUB_API_KEY'
```
→ Run: `source /etc/myapi/api-keys.conf`

**Port Conflict:**
```
Port 8080 was already in use
```
→ Run: `kill $(lsof -ti:8080)`

---

## License

MIT License
