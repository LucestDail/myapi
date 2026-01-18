#!/bin/bash

#############################################
# MyAPI Run Script
# Ubuntu 24.04 Server
#
# 사용법:
#   ./run.sh          : 포그라운드 실행
#   ./run.sh start    : 백그라운드 실행
#   ./run.sh stop     : 서버 중지
#   ./run.sh restart  : 서버 재시작
#   ./run.sh status   : 상태 확인
#   ./run.sh log      : 로그 확인
#############################################

set -e

# 설정
CONF_FILE="/etc/myapi/conf"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR_FILE="$SCRIPT_DIR/target/myapi-0.0.1-SNAPSHOT.jar"
LOG_FILE="$SCRIPT_DIR/myapi.log"
PID_FILE="$SCRIPT_DIR/myapi.pid"

# 색상
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# 설정 파일 로드
load_api_keys() {
    if [ ! -f "$CONF_FILE" ]; then
        echo -e "${RED}❌ 설정 파일이 존재하지 않습니다: $CONF_FILE${NC}"
        echo "먼저 build.sh를 실행하여 가이드를 확인하세요."
        exit 1
    fi
    source "$CONF_FILE"
    export FINNHUB_API_KEY
    export OPENWEATHER_API_KEY
    export AIRKOREA_API_KEY
    export EMERGENCY_API_SERVICE_KEY
    export TRAFFIC_API_KEY
    export GEMINI_API_KEY
    export NEWS_DB_JDBC_URL
    export NEWS_DB_USERNAME
    export NEWS_DB_PASSWORD
}

# JAR 파일 확인
check_jar() {
    if [ ! -f "$JAR_FILE" ]; then
        echo -e "${RED}❌ JAR 파일이 존재하지 않습니다.${NC}"
        echo "먼저 ./build.sh를 실행하세요."
        exit 1
    fi
}

# PID 가져오기
get_pid() {
    if [ -f "$PID_FILE" ]; then
        cat "$PID_FILE"
    else
        pgrep -f "myapi-0.0.1-SNAPSHOT.jar" 2>/dev/null || echo ""
    fi
}

# 서버 실행 중인지 확인
is_running() {
    local pid=$(get_pid)
    if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
        return 0
    fi
    return 1
}

# 포그라운드 실행
run_foreground() {
    load_api_keys
    check_jar
    echo -e "${GREEN}🚀 MyAPI 서버 시작 (포그라운드)${NC}"
    echo "   URL: http://localhost:8080"
    echo "   종료: Ctrl+C"
    echo ""
    java -jar "$JAR_FILE"
}

# 백그라운드 실행
start_server() {
    if is_running; then
        echo -e "${YELLOW}⚠️  서버가 이미 실행 중입니다. (PID: $(get_pid))${NC}"
        exit 1
    fi

    load_api_keys
    check_jar

    echo -e "${GREEN}🚀 MyAPI 서버 시작 (백그라운드)${NC}"
    nohup java -jar "$JAR_FILE" > "$LOG_FILE" 2>&1 &
    echo $! > "$PID_FILE"
    
    sleep 3
    
    if is_running; then
        echo -e "${GREEN}✅ 서버 시작 완료${NC}"
        echo "   PID: $(get_pid)"
        echo "   URL: http://localhost:8080"
        echo "   로그: $LOG_FILE"
    else
        echo -e "${RED}❌ 서버 시작 실패. 로그를 확인하세요:${NC}"
        echo "   tail -f $LOG_FILE"
        exit 1
    fi
}

# 서버 중지
stop_server() {
    local pid=$(get_pid)
    
    if [ -z "$pid" ]; then
        echo -e "${YELLOW}⚠️  실행 중인 서버가 없습니다.${NC}"
        rm -f "$PID_FILE"
        exit 0
    fi

    echo -e "${YELLOW}🛑 서버 중지 중... (PID: $pid)${NC}"
    kill "$pid" 2>/dev/null
    
    # 종료 대기 (최대 10초)
    for i in {1..10}; do
        if ! kill -0 "$pid" 2>/dev/null; then
            break
        fi
        sleep 1
    done
    
    # 강제 종료
    if kill -0 "$pid" 2>/dev/null; then
        echo "강제 종료 중..."
        kill -9 "$pid" 2>/dev/null
    fi
    
    rm -f "$PID_FILE"
    echo -e "${GREEN}✅ 서버 중지 완료${NC}"
}

# 서버 재시작
restart_server() {
    stop_server
    sleep 2
    start_server
}

# 상태 확인
status_server() {
    if is_running; then
        echo -e "${GREEN}✅ 서버 실행 중${NC}"
        echo "   PID: $(get_pid)"
        echo "   URL: http://localhost:8080"
    else
        echo -e "${RED}❌ 서버가 실행되고 있지 않습니다.${NC}"
    fi
}

# 로그 확인
show_log() {
    if [ -f "$LOG_FILE" ]; then
        echo -e "${YELLOW}📋 로그 파일: $LOG_FILE${NC}"
        echo "   (Ctrl+C로 종료)"
        echo ""
        tail -f "$LOG_FILE"
    else
        echo -e "${RED}❌ 로그 파일이 없습니다.${NC}"
    fi
}

# 도움말
show_help() {
    echo "사용법: ./run.sh [명령]"
    echo ""
    echo "명령:"
    echo "  (없음)    포그라운드 실행"
    echo "  start     백그라운드 실행"
    echo "  stop      서버 중지"
    echo "  restart   서버 재시작"
    echo "  status    상태 확인"
    echo "  log       로그 확인 (tail -f)"
    echo "  help      도움말"
}

# 메인
case "${1:-}" in
    start)
        start_server
        ;;
    stop)
        stop_server
        ;;
    restart)
        restart_server
        ;;
    status)
        status_server
        ;;
    log)
        show_log
        ;;
    help|--help|-h)
        show_help
        ;;
    "")
        run_foreground
        ;;
    *)
        echo -e "${RED}❌ 알 수 없는 명령: $1${NC}"
        show_help
        exit 1
        ;;
esac
