#!/bin/bash

#############################################
# MyAPI Run Script (JAR 실행)
# Ubuntu 24.04 Server
#############################################

set -e

# API 키 설정 파일 경로
API_KEYS_FILE="/etc/myapi/api-keys.conf"

# API 키 파일 확인 및 로드
if [ ! -f "$API_KEYS_FILE" ]; then
    echo "❌ API 키 파일이 존재하지 않습니다: $API_KEYS_FILE"
    echo "먼저 build.sh를 실행하여 가이드를 확인하세요."
    exit 1
fi

source "$API_KEYS_FILE"
export FINNHUB_API_KEY
export OPENWEATHER_API_KEY

# 프로젝트 디렉토리로 이동
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# JAR 파일 확인
JAR_FILE="target/myapi-0.0.1-SNAPSHOT.jar"
if [ ! -f "$JAR_FILE" ]; then
    echo "❌ JAR 파일이 존재하지 않습니다. 먼저 build.sh를 실행하세요."
    exit 1
fi

echo "🚀 MyAPI 서버 시작..."
echo "   URL: http://localhost:8080"
echo ""

java -jar "$JAR_FILE"
