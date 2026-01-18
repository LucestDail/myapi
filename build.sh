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
API_KEYS_FILE="/etc/myapi/api-keys.conf"

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  MyAPI Build & Run Script${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

# 1. API 키 파일 확인
echo -e "${YELLOW}[1/4] API 키 파일 확인...${NC}"
if [ ! -f "$API_KEYS_FILE" ]; then
    echo -e "${RED}❌ API 키 파일이 존재하지 않습니다: $API_KEYS_FILE${NC}"
    echo ""
    echo -e "${YELLOW}다음 명령어로 API 키 파일을 생성하세요:${NC}"
    echo ""
    echo "  sudo mkdir -p /etc/myapi"
    echo "  sudo tee /etc/myapi/api-keys.conf > /dev/null << 'EOF'"
    echo "# MyAPI Configuration"
    echo "export FINNHUB_API_KEY=\"your_finnhub_api_key_here\""
    echo "export OPENWEATHER_API_KEY=\"your_openweather_api_key_here\""
    echo "export AIRKOREA_API_KEY=\"your_airkorea_api_key_here\""
    echo "EOF"
    echo "  sudo chmod 600 /etc/myapi/api-keys.conf"
    echo ""
    exit 1
fi

# API 키 로드
source "$API_KEYS_FILE"

# API 키 검증
if [ -z "$FINNHUB_API_KEY" ] || [ "$FINNHUB_API_KEY" == "your_finnhub_api_key_here" ]; then
    echo -e "${RED}❌ FINNHUB_API_KEY가 설정되지 않았습니다.${NC}"
    exit 1
fi

if [ -z "$OPENWEATHER_API_KEY" ] || [ "$OPENWEATHER_API_KEY" == "your_openweather_api_key_here" ]; then
    echo -e "${RED}❌ OPENWEATHER_API_KEY가 설정되지 않았습니다.${NC}"
    exit 1
fi

if [ -z "$AIRKOREA_API_KEY" ] || [ "$AIRKOREA_API_KEY" == "your_airkorea_api_key_here" ]; then
    echo -e "${RED}❌ AIRKOREA_API_KEY가 설정되지 않았습니다.${NC}"
    exit 1
fi

echo -e "${GREEN}✅ API 키 로드 완료${NC}"
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

# 빌드
echo "Maven 빌드 중..."
mvn clean package -DskipTests -q

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  빌드 완료!${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo -e "실행 방법:"
echo -e "  ${YELLOW}source /etc/myapi/api-keys.conf && java -jar target/myapi-0.0.1-SNAPSHOT.jar${NC}"
echo ""
echo -e "또는 Maven으로 실행:"
echo -e "  ${YELLOW}source /etc/myapi/api-keys.conf && mvn spring-boot:run${NC}"
echo ""
