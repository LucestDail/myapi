#!/bin/bash

#############################################
# MyAPI Build & Run Script
# Ubuntu 24.04 Server
#############################################

set -e

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# API 키 설정 파일 경로
CONF_FILE="/etc/myapi/conf"

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  MyAPI Build & Run Script${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

# 1. 설정 파일 확인
echo -e "${YELLOW}[1/4] 설정 파일 확인...${NC}"
if [ ! -f "$CONF_FILE" ]; then
    echo -e "${RED}❌ 설정 파일이 존재하지 않습니다: $CONF_FILE${NC}"
    echo ""
    echo -e "${YELLOW}다음 명령어로 설정 파일을 생성하세요:${NC}"
    echo ""
    echo "  sudo mkdir -p /etc/myapi"
    echo "  sudo nano /etc/myapi/conf"
    echo ""
    echo -e "${YELLOW}또는 conf 파일 예시를 참고하세요.${NC}"
    echo ""
    exit 1
fi

# 설정 파일 로드
source "$CONF_FILE"

# 필수 환경변수 검증
REQUIRED_VARS=(
    "FINNHUB_API_KEY"
    "OPENWEATHER_API_KEY"
    "AIRKOREA_API_KEY"
    "EMERGENCY_API_SERVICE_KEY"
    "TRAFFIC_API_KEY"
    "GEMINI_API_KEY"
    "NEWS_DB_JDBC_URL"
    "NEWS_DB_USERNAME"
    "NEWS_DB_PASSWORD"
)

MISSING_VARS=()
for var in "${REQUIRED_VARS[@]}"; do
    if [ -z "${!var}" ]; then
        MISSING_VARS+=("$var")
    fi
done

if [ ${#MISSING_VARS[@]} -gt 0 ]; then
    echo -e "${RED}❌ 다음 환경변수가 설정되지 않았습니다:${NC}"
    for var in "${MISSING_VARS[@]}"; do
        echo -e "  ${RED}- $var${NC}"
    done
    exit 1
fi

echo -e "${GREEN}✅ 설정 파일 로드 완료${NC}"
echo ""

# 2. Java 버전 확인
echo -e "${YELLOW}[2/4] Java 버전 확인...${NC}"
if ! command -v java &> /dev/null; then
    echo -e "${RED}❌ Java가 설치되어 있지 않습니다.${NC}"
    echo "  sudo apt install openjdk-17-jdk -y"
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 17 ]; then
    echo -e "${RED}❌ Java 17 이상이 필요합니다. 현재: $JAVA_VERSION${NC}"
    exit 1
fi
echo -e "${GREEN}✅ Java $JAVA_VERSION 확인 완료${NC}"
echo ""

# 3. Maven 확인
echo -e "${YELLOW}[3/4] Maven 확인...${NC}"
if ! command -v mvn &> /dev/null; then
    echo -e "${RED}❌ Maven이 설치되어 있지 않습니다.${NC}"
    echo "  sudo apt install maven -y"
    exit 1
fi
echo -e "${GREEN}✅ Maven 확인 완료${NC}"
echo ""

# 4. 빌드 및 실행
echo -e "${YELLOW}[4/4] 빌드 및 실행...${NC}"

# 프로젝트 디렉토리로 이동
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# 환경변수 export
export FINNHUB_API_KEY
export OPENWEATHER_API_KEY
export AIRKOREA_API_KEY
export EMERGENCY_API_SERVICE_KEY
export TRAFFIC_API_KEY
export GEMINI_API_KEY
export NEWS_DB_JDBC_URL
export NEWS_DB_USERNAME
export NEWS_DB_PASSWORD

# 빌드
echo "Maven 빌드 중..."
mvn clean package -DskipTests -q

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  빌드 완료!${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo -e "실행 방법:"
echo -e "  ${YELLOW}source /etc/myapi/conf && java -jar target/myapi-0.0.1-SNAPSHOT.jar${NC}"
echo ""
echo -e "또는 ./run.sh 사용:"
echo -e "  ${YELLOW}./run.sh start${NC}"
echo ""
