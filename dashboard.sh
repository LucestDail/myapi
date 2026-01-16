#!/bin/bash

#############################################
# MyAPI 실시간 대시보드
# 주식 정보 + 날씨 정보 터미널 대시보드
#
# - 화면: 매초 갱신
# - 데이터: 60초마다 갱신 (서버 캐시)
#############################################

# 서버 주소 설정
API_SERVER="${API_SERVER:-http://localhost:8080}"

# 색상 정의
RESET='\033[0m'
BOLD='\033[1m'
DIM='\033[2m'

# 전경색
BLACK='\033[30m'
RED='\033[31m'
GREEN='\033[32m'
YELLOW='\033[33m'
BLUE='\033[34m'
MAGENTA='\033[35m'
CYAN='\033[36m'
WHITE='\033[37m'

# 밝은 색상
BRIGHT_RED='\033[91m'
BRIGHT_GREEN='\033[92m'
BRIGHT_YELLOW='\033[93m'
BRIGHT_BLUE='\033[94m'
BRIGHT_MAGENTA='\033[95m'
BRIGHT_CYAN='\033[96m'

# 캐시 변수
CACHE_LOCATION=""
CACHE_STOCKS=""
CACHE_WEATHER=""
CACHE_TIME=0
CACHE_INTERVAL=60  # 60초마다 데이터 갱신

# 터미널 크기 가져오기
get_terminal_size() {
    TERM_WIDTH=$(tput cols)
    TERM_HEIGHT=$(tput lines)
}

# 중앙 정렬 출력
print_center() {
    local text="$1"
    local color="${2:-$RESET}"
    local plain_text=$(echo -e "$text" | sed 's/\x1b\[[0-9;]*m//g')
    local text_len=${#plain_text}
    local padding=$(( (TERM_WIDTH - text_len) / 2 ))
    [ $padding -lt 0 ] && padding=0
    printf "%${padding}s" ""
    echo -e "${color}${text}${RESET}"
}

# 구분선 출력
print_line() {
    local char="${1:-─}"
    local color="${2:-$DIM}"
    echo -e "${color}$(printf '%*s' "$TERM_WIDTH" '' | tr ' ' "$char")${RESET}"
}

# 박스 헤더
print_header() {
    local title="$1"
    local color="${2:-$BRIGHT_CYAN}"
    echo ""
    print_line "═" "$color"
    print_center "  $title  " "${BOLD}${color}"
    print_line "═" "$color"
}

# 데이터 새로고침 필요 여부 확인
need_refresh() {
    local now=$(date +%s)
    local diff=$((now - CACHE_TIME))
    [ $diff -ge $CACHE_INTERVAL ]
}

# 모든 데이터 가져오기 (캐시)
fetch_all_data() {
    if need_refresh; then
        # 위치 날씨 (서버 캐시)
        CACHE_LOCATION=$(curl -s --connect-timeout 3 "${API_SERVER}/api/location/weather" 2>/dev/null)
        
        # 주식 데이터 (병렬 호출)
        local symbols=("SPY" "QQQ" "NVDA" "SNPS" "REKR" "SMCX")
        CACHE_STOCKS=""
        for symbol in "${symbols[@]}"; do
            local data=$(curl -s --connect-timeout 3 "${API_SERVER}/api/finnhub/quote?symbol=${symbol}" 2>/dev/null)
            CACHE_STOCKS="${CACHE_STOCKS}${symbol}:${data}|"
        done
        
        # 날씨 데이터 (서버 캐시)
        CACHE_WEATHER=$(curl -s --connect-timeout 3 "${API_SERVER}/api/weather" 2>/dev/null)
        
        CACHE_TIME=$(date +%s)
    fi
}

# 현재 위치 정보 출력
print_location() {
    echo ""
    if [ -n "$CACHE_LOCATION" ] && [[ "$CACHE_LOCATION" == *"{"* ]]; then
        local weather=$(echo "$CACHE_LOCATION" | python3 -c "
import sys, json
try:
    d = json.load(sys.stdin)
    print(d.get('weather', d.get('rawResponse', '정보 없음')))
except:
    print('정보 없음')
" 2>/dev/null)
        print_center "📍 $weather" "${BOLD}${WHITE}"
    else
        print_center "📍 위치 정보 로딩 중..." "${DIM}"
    fi
}

# 시간 정보 출력
print_time() {
    local current_time=$(date '+%Y년 %m월 %d일 %A %H:%M:%S')
    local next_refresh=$((CACHE_INTERVAL - ($(date +%s) - CACHE_TIME)))
    [ $next_refresh -lt 0 ] && next_refresh=0
    print_center "🕐 $current_time  │  다음 갱신: ${next_refresh}초" "$DIM"
}

# 주식 섹션 출력
print_stocks() {
    print_header "📈 미국 주식 시세" "$BRIGHT_YELLOW"
    echo ""
    
    local symbols=("SPY" "QQQ" "NVDA" "SNPS" "REKR" "SMCX")
    local names=("S&P500 ETF" "나스닥100 ETF" "엔비디아" "시놉시스" "Rekor Systems" "SMC Corp")
    
    for i in "${!symbols[@]}"; do
        local symbol="${symbols[$i]}"
        local name="${names[$i]}"
        
        # 캐시에서 데이터 추출
        local data=$(echo "$CACHE_STOCKS" | grep -o "${symbol}:[^|]*" | cut -d':' -f2-)
        
        local price="N/A"
        local change="0"
        local pct="0"
        
        if [ -n "$data" ] && [[ "$data" == *"{"* ]]; then
            local result=$(echo "$data" | python3 -c "
import sys, json
try:
    d = json.load(sys.stdin)
    price = d.get('c', d.get('currentPrice', 0)) or 0
    change = d.get('d', d.get('change', 0)) or 0
    pct = d.get('dp', d.get('percentChange', 0)) or 0
    print(f'{price:.2f}|{change:.2f}|{pct:.2f}')
except:
    print('N/A|0|0')
" 2>/dev/null)
            price=$(echo "$result" | cut -d'|' -f1)
            change=$(echo "$result" | cut -d'|' -f2)
            pct=$(echo "$result" | cut -d'|' -f3)
        fi
        
        # 색상 결정
        local color="$WHITE"
        local arrow=""
        if [ "$price" != "N/A" ]; then
            if (( $(echo "$change > 0" | bc -l 2>/dev/null || echo 0) )); then
                color="$BRIGHT_GREEN"
                arrow="▲"
            elif (( $(echo "$change < 0" | bc -l 2>/dev/null || echo 0) )); then
                color="$BRIGHT_RED"
                arrow="▼"
            else
                arrow="─"
            fi
        fi
        
        # 출력 포맷
        printf "  ${BOLD}${CYAN}%-6s %-12s${RESET} " "$symbol" "$name"
        if [ "$price" != "N/A" ]; then
            printf "${color}\$%-8s %s%-6s (%s%%)${RESET}\n" "$price" "$arrow" "$change" "$pct"
        else
            printf "${DIM}로딩 중...${RESET}\n"
        fi
    done
    echo ""
}

# 날씨 섹션 출력
print_weather() {
    print_header "🌤️  한국 주요 도시 날씨" "$BRIGHT_BLUE"
    echo ""
    
    if [ -z "$CACHE_WEATHER" ] || [ "$CACHE_WEATHER" == "[]" ]; then
        print_center "날씨 정보 로딩 중..." "$DIM"
        return
    fi
    
    # 컬럼 수 계산 (터미널 너비에 따라)
    local item_width=24
    local cols=$(( TERM_WIDTH / item_width ))
    [ $cols -lt 1 ] && cols=1
    [ $cols -gt 5 ] && cols=5
    
    echo "$CACHE_WEATHER" | python3 -c "
import sys, json

try:
    data = json.load(sys.stdin)
except:
    print('  데이터 파싱 오류')
    sys.exit(0)

cols = $cols

def get_icon(weather):
    w = weather.lower() if weather else ''
    if 'clear' in w: return '☀️ '
    elif 'cloud' in w or 'overcast' in w: return '☁️ '
    elif 'rain' in w or 'drizzle' in w: return '🌧️'
    elif 'snow' in w: return '❄️ '
    elif 'mist' in w or 'fog' in w or 'haze' in w: return '🌫️'
    elif 'thunder' in w: return '⛈️ '
    else: return '🌡️ '

items = []
for city in data:
    name_ko = city.get('cityKo', city.get('city', ''))
    temp = city.get('temperatureCelsius', 0)
    weather = city.get('weather', '')
    humidity = city.get('humidity', 0)
    icon = get_icon(weather)
    
    # 온도에 따른 색상 코드
    if temp <= 0:
        temp_color = '\033[96m'  # cyan (추움)
    elif temp <= 10:
        temp_color = '\033[94m'  # blue
    elif temp <= 20:
        temp_color = '\033[92m'  # green
    elif temp <= 30:
        temp_color = '\033[93m'  # yellow
    else:
        temp_color = '\033[91m'  # red (더움)
    
    reset = '\033[0m'
    bold = '\033[1m'
    dim = '\033[2m'
    
    items.append(f'  {icon} {bold}{name_ko:4}{reset} {temp_color}{temp:5.1f}°C{reset} {dim}({humidity}%){reset}')

# 출력
for i, item in enumerate(items):
    print(item, end='')
    if (i + 1) % cols == 0:
        print()
    else:
        print('  ', end='')

if len(items) % cols != 0:
    print()
" 2>/dev/null
    echo ""
}

# 푸터 출력
print_footer() {
    print_line "─" "$DIM"
    local footer="서버: $API_SERVER │ 종료: Ctrl+C │ 데이터 갱신: ${CACHE_INTERVAL}초"
    print_center "$footer" "$DIM"
}

# 메인 화면 그리기
draw_screen() {
    get_terminal_size
    
    # 커서 홈으로 이동 (깜빡임 방지)
    tput cup 0 0
    
    # 타이틀
    echo ""
    print_center "╔════════════════════════════════════════╗" "$BRIGHT_MAGENTA"
    print_center "║       📊  MyAPI 실시간 대시보드  📊      ║" "${BOLD}${BRIGHT_MAGENTA}"
    print_center "╚════════════════════════════════════════╝" "$BRIGHT_MAGENTA"
    
    # 현재 위치 날씨
    print_location
    
    # 시간
    print_time
    
    # 주식 정보
    print_stocks
    
    # 날씨 정보
    print_weather
    
    # 푸터
    print_footer
    
    # 남은 공간 클리어
    tput ed
}

# 실시간 대시보드 실행
run_dashboard() {
    # 화면 초기화
    clear
    tput civis  # 커서 숨기기
    
    # 종료 시 커서 복원
    trap 'tput cnorm; echo ""; exit 0' INT TERM
    
    # 초기 데이터 로드
    fetch_all_data
    
    # 매초 화면 갱신
    while true; do
        fetch_all_data  # 필요시에만 갱신됨 (내부 체크)
        draw_screen
        sleep 1
    done
}

# 도움말
show_help() {
    echo "사용법: ./dashboard.sh [옵션]"
    echo ""
    echo "옵션:"
    echo "  (없음)         실시간 대시보드 실행"
    echo "  -s, --server   서버 주소 지정"
    echo "  -h, --help     도움말"
    echo ""
    echo "예시:"
    echo "  ./dashboard.sh"
    echo "  ./dashboard.sh --server http://localhost:8080"
    echo "  API_SERVER=http://localhost:8080 ./dashboard.sh"
}

# 인자 처리
while [[ $# -gt 0 ]]; do
    case $1 in
        -s|--server)
            API_SERVER="$2"
            shift 2
            ;;
        -h|--help)
            show_help
            exit 0
            ;;
        *)
            echo "알 수 없는 옵션: $1"
            show_help
            exit 1
            ;;
    esac
done

# 실행
run_dashboard
