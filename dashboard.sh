#!/bin/bash

#############################################
# MyAPI 실시간 대시보드
# 주식 정보 + 날씨 정보 + 시스템 상태
#############################################

# 서버 주소 설정
API_SERVER="${API_SERVER:-http://localhost:8080}"

# 색상 정의
RESET='\033[0m'
BOLD='\033[1m'
DIM='\033[2m'

BLACK='\033[30m'
RED='\033[31m'
GREEN='\033[32m'
YELLOW='\033[33m'
BLUE='\033[34m'
MAGENTA='\033[35m'
CYAN='\033[36m'
WHITE='\033[37m'

BRIGHT_RED='\033[91m'
BRIGHT_GREEN='\033[92m'
BRIGHT_YELLOW='\033[93m'
BRIGHT_BLUE='\033[94m'
BRIGHT_MAGENTA='\033[95m'
BRIGHT_CYAN='\033[96m'

# 캐시 변수 (외부 API: 60초, 내부: 매초)
CACHE_LOCATION=""
CACHE_STOCKS=""
CACHE_WEATHER=""
CACHE_SYSTEM=""
CACHE_TIME=0
CACHE_INTERVAL=60  # 외부 API 캐시 (위치, 주식, 날씨)

# 고양이 애니메이션 프레임
CAT_FRAME=0

# 터미널 크기
TERM_WIDTH=80
TERM_HEIGHT=24

get_terminal_size() {
    TERM_WIDTH=$(tput cols 2>/dev/null || echo 80)
    TERM_HEIGHT=$(tput lines 2>/dev/null || echo 24)
    [ $TERM_WIDTH -lt 60 ] && TERM_WIDTH=60
}

# 반복 문자
repeat_char() {
    printf "%${2}s" | tr ' ' "$1"
}

# 중앙 정렬
center_text() {
    local text="$1"
    local plain=$(echo -e "$text" | sed 's/\x1b\[[0-9;]*m//g')
    local len=${#plain}
    local pad=$(( (TERM_WIDTH - len) / 2 ))
    [ $pad -lt 0 ] && pad=0
    printf "%${pad}s" ""
    echo -e "$text"
}

# 전체 너비 테두리
border_line() {
    local color="${1:-$CYAN}"
    echo -e "${color}║$(repeat_char '═' $((TERM_WIDTH - 2)))║${RESET}"
}

border_top() {
    local color="${1:-$CYAN}"
    echo -e "${color}╔$(repeat_char '═' $((TERM_WIDTH - 2)))╗${RESET}"
}

border_bottom() {
    local color="${1:-$CYAN}"
    echo -e "${color}╚$(repeat_char '═' $((TERM_WIDTH - 2)))╝${RESET}"
}

section_line() {
    local color="${1:-$DIM}"
    echo -e "${color}╟$(repeat_char '─' $((TERM_WIDTH - 2)))╢${RESET}"
}

# 테두리 안에 텍스트
border_text() {
    local text="$1"
    local color="${2:-$CYAN}"
    local plain=$(echo -e "$text" | sed 's/\x1b\[[0-9;]*m//g')
    local len=${#plain}
    local pad=$((TERM_WIDTH - len - 4))
    [ $pad -lt 0 ] && pad=0
    echo -e "${color}║${RESET} ${text}$(printf "%${pad}s" "") ${color}║${RESET}"
}

border_center() {
    local text="$1"
    local color="${2:-$CYAN}"
    local plain=$(echo -e "$text" | sed 's/\x1b\[[0-9;]*m//g')
    local len=${#plain}
    local total_pad=$((TERM_WIDTH - len - 2))
    local left_pad=$((total_pad / 2))
    local right_pad=$((total_pad - left_pad))
    [ $left_pad -lt 0 ] && left_pad=0
    [ $right_pad -lt 0 ] && right_pad=0
    echo -e "${color}║${RESET}$(printf "%${left_pad}s" "")${text}$(printf "%${right_pad}s" "")${color}║${RESET}"
}

# 고양이 ASCII 아트
print_cat() {
    local frame=$((CAT_FRAME % 6))
    local color="$BRIGHT_YELLOW"
    
    case $frame in
        0)
            border_center "${color}   /\\_/\\   ${RESET}" "$BRIGHT_MAGENTA"
            border_center "${color}  ( o.o )  ${BRIGHT_CYAN}~ meow ~${RESET}" "$BRIGHT_MAGENTA"
            border_center "${color}   > ^ <   ${RESET}" "$BRIGHT_MAGENTA"
            ;;
        1)
            border_center "${color}   /\\_/\\   ${RESET}" "$BRIGHT_MAGENTA"
            border_center "${color}  ( -.- )  ${DIM}zzZ${RESET}" "$BRIGHT_MAGENTA"
            border_center "${color}   > ^ <   ${RESET}" "$BRIGHT_MAGENTA"
            ;;
        2)
            border_center "${color}   /\\_/\\   ${RESET}" "$BRIGHT_MAGENTA"
            border_center "${color}  ( ^.^ )  ${BRIGHT_GREEN}♪${RESET}" "$BRIGHT_MAGENTA"
            border_center "${color}  ~( ^ )~  ${RESET}" "$BRIGHT_MAGENTA"
            ;;
        3)
            border_center "${color}    /\\_/\\  ${RESET}" "$BRIGHT_MAGENTA"
            border_center "${color}   ( o.o ) ${BRIGHT_CYAN})${RESET}" "$BRIGHT_MAGENTA"
            border_center "${color}   ~> ^ <~ ${RESET}" "$BRIGHT_MAGENTA"
            ;;
        4)
            border_center "${color}  /\\_/\\    ${RESET}" "$BRIGHT_MAGENTA"
            border_center "${color} ( >.< )   ${BRIGHT_RED}!${RESET}" "$BRIGHT_MAGENTA"
            border_center "${color}  > ^ <    ${RESET}" "$BRIGHT_MAGENTA"
            ;;
        5)
            border_center "${color}   /\\_/\\   ${RESET}" "$BRIGHT_MAGENTA"
            border_center "${color}  ( =.= )  ${BRIGHT_MAGENTA}♥${RESET}" "$BRIGHT_MAGENTA"
            border_center "${color}   > ^ <   ${RESET}" "$BRIGHT_MAGENTA"
            ;;
    esac
    CAT_FRAME=$((CAT_FRAME + 1))
}

# 데이터 갱신 필요 여부
need_refresh() {
    local now=$(date +%s)
    [ $((now - CACHE_TIME)) -ge $CACHE_INTERVAL ]
}

# 데이터 가져오기
fetch_all_data() {
    # 외부 API 데이터 (60초 캐시)
    if need_refresh; then
        CACHE_LOCATION=$(curl -s --connect-timeout 2 "${API_SERVER}/api/location/weather" 2>/dev/null)
        
        # 주식 티커 (10개)
        local symbols=("SPY" "QQQ" "NVDA" "TSLA" "SNPS" "REKR" "SMCX" "ETHU" "BITX" "GLDM")
        CACHE_STOCKS=""
        for symbol in "${symbols[@]}"; do
            local data=$(curl -s --connect-timeout 2 "${API_SERVER}/api/finnhub/quote?symbol=${symbol}" 2>/dev/null)
            CACHE_STOCKS="${CACHE_STOCKS}${symbol}|${data};"
        done
        
        CACHE_WEATHER=$(curl -s --connect-timeout 2 "${API_SERVER}/api/weather" 2>/dev/null)
        CACHE_TIME=$(date +%s)
    fi
    
    # 시스템 상태 (매초 갱신 - 내부 API라 부담 없음)
    CACHE_SYSTEM=$(curl -s --connect-timeout 1 "${API_SERVER}/api/system/status" 2>/dev/null)
}

# 화면 그리기
draw_screen() {
    get_terminal_size
    local next_refresh=$((CACHE_INTERVAL - ($(date +%s) - CACHE_TIME)))
    [ $next_refresh -lt 0 ] && next_refresh=0
    
    # 버퍼에 출력
    local output=""
    
    # === 상단 테두리 ===
    output+="$(border_top "$BRIGHT_MAGENTA")\n"
    
    # === 고양이 + 타이틀 ===
    print_cat_to_output() {
        local frame=$((CAT_FRAME % 6))
        local color="$BRIGHT_YELLOW"
        case $frame in
            0) c1="   /\\_/\\   "; c2="  ( o.o )  "; c3="   > ^ <   " ;;
            1) c1="   /\\_/\\   "; c2="  ( -.- )  "; c3="   > ^ <   " ;;
            2) c1="   /\\_/\\   "; c2="  ( ^.^ )  "; c3="  ~( ^ )~  " ;;
            3) c1="    /\\_/\\  "; c2="   ( o.o ) "; c3="   ~> ^ <~ " ;;
            4) c1="  /\\_/\\    "; c2=" ( >.< )   "; c3="  > ^ <    " ;;
            5) c1="   /\\_/\\   "; c2="  ( =.= )  "; c3="   > ^ <   " ;;
        esac
        CAT_FRAME=$((CAT_FRAME + 1))
        echo "${color}${c1}${RESET}"
        echo "${color}${c2}${RESET}"
        echo "${color}${c3}${RESET}"
    }
    
    output+="$(border_center "" "$BRIGHT_MAGENTA")\n"
    output+="$(border_center "${BRIGHT_YELLOW}   /\\_/\\   ${RESET}${BOLD}${WHITE} MyAPI 실시간 대시보드${RESET}" "$BRIGHT_MAGENTA")\n"
    
    local cat_expr=""
    case $((CAT_FRAME % 6)) in
        0) cat_expr="( o.o )  ~meow~" ;;
        1) cat_expr="( -.- )  zzZ" ;;
        2) cat_expr="( ^.^ )  ♪" ;;
        3) cat_expr="( o.o )  ?" ;;
        4) cat_expr="( >.< )  !" ;;
        5) cat_expr="( =.= )  ♥" ;;
    esac
    CAT_FRAME=$((CAT_FRAME + 1))
    
    output+="$(border_center "${BRIGHT_YELLOW}  ${cat_expr}${RESET}   ${DIM}$(date '+%Y-%m-%d %H:%M:%S')${RESET}" "$BRIGHT_MAGENTA")\n"
    output+="$(border_center "${BRIGHT_YELLOW}   > ^ <   ${RESET}   ${BRIGHT_CYAN}다음 갱신: ${next_refresh}초${RESET}" "$BRIGHT_MAGENTA")\n"
    output+="$(border_center "" "$BRIGHT_MAGENTA")\n"
    
    # === 위치 날씨 ===
    output+="$(section_line "$BRIGHT_MAGENTA")\n"
    local loc_weather="위치 정보 로딩 중..."
    if [ -n "$CACHE_LOCATION" ] && [[ "$CACHE_LOCATION" == *"weather"* ]]; then
        loc_weather=$(echo "$CACHE_LOCATION" | python3 -c "import sys,json;d=json.load(sys.stdin);print(d.get('weather','정보 없음'))" 2>/dev/null)
    fi
    output+="$(border_center "${BOLD}📍 ${loc_weather}${RESET}" "$BRIGHT_MAGENTA")\n"
    
    # === 주식 시세 ===
    output+="$(section_line "$BRIGHT_YELLOW")\n"
    output+="$(border_center "${BOLD}${BRIGHT_YELLOW}📈 미국 주식 시세${RESET}" "$BRIGHT_YELLOW")\n"
    output+="$(section_line "$BRIGHT_YELLOW")\n"
    
    local symbols=("SPY" "QQQ" "NVDA" "TSLA" "SNPS" "REKR" "SMCX" "ETHU" "BITX" "GLDM")
    local names=("S&P500 ETF" "나스닥100 ETF" "엔비디아" "테슬라" "시놉시스" "Rekor Systems" "SMC Corp" "이더리움 2X" "비트코인 2X" "금 ETF")
    
    for i in "${!symbols[@]}"; do
        local sym="${symbols[$i]}"
        local name="${names[$i]}"
        local raw=$(echo "$CACHE_STOCKS" | grep -o "${sym}|[^;]*" | cut -d'|' -f2)
        
        local price="---" change="0.00" pct="0.00" color="$WHITE" arrow="─"
        if [ -n "$raw" ] && [[ "$raw" == *"{"* ]]; then
            read price change pct <<< $(echo "$raw" | python3 -c "
import sys,json
try:
    d=json.load(sys.stdin)
    p=d.get('c',0) or 0
    c=d.get('d',0) or 0
    dp=d.get('dp',0) or 0
    print(f'{p:.2f} {c:+.2f} {dp:+.2f}')
except:
    print('--- 0.00 0.00')
" 2>/dev/null)
            if [[ "$change" == +* ]] && [ "$change" != "+0.00" ]; then
                color="$BRIGHT_GREEN"; arrow="▲"
            elif [[ "$change" == -* ]]; then
                color="$BRIGHT_RED"; arrow="▼"
            fi
        fi
        
        local stock_text=$(printf "${BOLD}${CYAN}%-6s${RESET} %-12s ${color}\$%-9s %s%-7s (%s%%)${RESET}" "$sym" "$name" "$price" "$arrow" "$change" "$pct")
        output+="$(border_text "$stock_text" "$BRIGHT_YELLOW")\n"
    done
    
    # === 날씨 ===
    output+="$(section_line "$BRIGHT_BLUE")\n"
    output+="$(border_center "${BOLD}${BRIGHT_BLUE}🌤️ 한국 주요 도시 날씨${RESET}" "$BRIGHT_BLUE")\n"
    output+="$(section_line "$BRIGHT_BLUE")\n"
    
    if [ -n "$CACHE_WEATHER" ] && [[ "$CACHE_WEATHER" == *"["* ]]; then
        echo "$CACHE_WEATHER" | python3 -c "
import sys,json
data=json.load(sys.stdin)
icons={'clear':'☀️','cloud':'☁️','rain':'🌧️','snow':'❄️','mist':'🌫️','fog':'🌫️','haze':'🌫️','thunder':'⛈️'}
def icon(w):
    w=w.lower() if w else ''
    for k,v in icons.items():
        if k in w: return v
    return '🌡️'
def tcolor(t):
    if t<=0: return '\033[96m'
    elif t<=10: return '\033[94m'
    elif t<=20: return '\033[92m'
    elif t<=30: return '\033[93m'
    return '\033[91m'
r='\033[0m'
b='\033[1m'
d='\033[2m'
for c in data:
    nm=c.get('cityKo','')
    t=c.get('temperatureCelsius',0)
    w=c.get('weather','')
    h=c.get('humidity',0)
    print(f'{icon(w)} {b}{nm:4}{r} {tcolor(t)}{t:5.1f}°C{r} {d}습도 {h}%{r}')
" 2>/dev/null | while read line; do
            output+="$(border_text "  $line" "$BRIGHT_BLUE")\n"
            echo "$line"
        done | while read line; do
            border_text "  $line" "$BRIGHT_BLUE"
        done >> /dev/null
        
        # 날씨 출력 (간단히)
        local weather_lines=$(echo "$CACHE_WEATHER" | python3 -c "
import sys,json
data=json.load(sys.stdin)
icons={'clear':'☀️','cloud':'☁️','rain':'🌧️','snow':'❄️','mist':'🌫️','fog':'🌫️','haze':'🌫️','thunder':'⛈️'}
def icon(w):
    w=w.lower() if w else ''
    for k,v in icons.items():
        if k in w: return v
    return '🌡️'
def tcolor(t):
    if t<=0: return '\033[96m'
    elif t<=10: return '\033[94m'
    elif t<=20: return '\033[92m'
    elif t<=30: return '\033[93m'
    return '\033[91m'
r='\033[0m'
b='\033[1m'
d='\033[2m'
for c in data:
    nm=c.get('cityKo','')
    t=c.get('temperatureCelsius',0)
    w=c.get('weather','')
    h=c.get('humidity',0)
    print(f'{icon(w)} {b}{nm:4}{r} {tcolor(t)}{t:5.1f}°C{r} {d}습도 {h}%{r}')
" 2>/dev/null)
        while IFS= read -r line; do
            output+="$(border_text "  $line" "$BRIGHT_BLUE")\n"
        done <<< "$weather_lines"
    fi
    
    # === 시스템 상태 ===
    output+="$(section_line "$BRIGHT_GREEN")\n"
    output+="$(border_center "${BOLD}${BRIGHT_GREEN}💻 서버 시스템 상태${RESET}" "$BRIGHT_GREEN")\n"
    output+="$(section_line "$BRIGHT_GREEN")\n"
    
    if [ -n "$CACHE_SYSTEM" ] && [[ "$CACHE_SYSTEM" == *"{"* ]]; then
        local sys_lines=$(echo "$CACHE_SYSTEM" | python3 -c "
import sys,json
d=json.load(sys.stdin)
def fmt(b):
    for u in ['B','KB','MB','GB','TB']:
        if b<1024: return f'{b:.1f}{u}'
        b/=1024
    return f'{b:.1f}PB'
def bar(pct,w=20):
    filled=int(pct/100*w)
    g,y,r,rs='\033[92m','\033[93m','\033[91m','\033[0m'
    col=g if pct<60 else y if pct<80 else r
    return f'{col}'+'█'*filled+'░'*(w-filled)+f'{rs}'
b='\033[1m'
rs='\033[0m'
cpu=d.get('systemCpuLoad',-1)
proc_cpu=d.get('processCpuLoad',-1)
mem_pct=d.get('memoryUsagePercent',0)
mem_used=d.get('usedPhysicalMemory',0)
mem_total=d.get('totalPhysicalMemory',0)
heap_pct=d.get('heapUsagePercent',0)
heap_used=d.get('heapUsed',0)
heap_max=d.get('heapMax',0)
threads=d.get('threadCount',0)
peak_threads=d.get('peakThreadCount',0)
gc_count=d.get('gcCount',0)
gc_time=d.get('gcTime',0)
classes=d.get('loadedClassCount',0)
uptime=d.get('uptimeMillis',0)//1000
up_h,up_m,up_s=uptime//3600,(uptime%3600)//60,uptime%60
print(f'{b}시스템 CPU{rs}  {cpu:6.1f}%  {bar(max(0,cpu))}')
print(f'{b}프로세스 CPU{rs} {proc_cpu:5.1f}%  {bar(max(0,proc_cpu))}')
print(f'{b}물리 메모리{rs}  {mem_pct:5.1f}%  {bar(mem_pct)}  {fmt(mem_used)} / {fmt(mem_total)}')
print(f'{b}JVM Heap{rs}    {heap_pct:5.1f}%  {bar(heap_pct)}  {fmt(heap_used)} / {fmt(heap_max)}')
print(f'{b}스레드{rs}       {threads} (최대 {peak_threads})   {b}GC{rs} {gc_count}회 / {gc_time}ms   {b}클래스{rs} {classes}개')
print(f'{b}Uptime{rs}      {up_h}시간 {up_m}분 {up_s}초')
" 2>/dev/null)
        while IFS= read -r line; do
            output+="$(border_text "  $line" "$BRIGHT_GREEN")\n"
        done <<< "$sys_lines"
    else
        output+="$(border_text "  시스템 정보 로딩 중..." "$BRIGHT_GREEN")\n"
    fi
    
    # === 하단 테두리 ===
    output+="$(section_line "$BRIGHT_MAGENTA")\n"
    output+="$(border_center "${DIM}서버: ${API_SERVER} │ Ctrl+C: 종료${RESET}" "$BRIGHT_MAGENTA")\n"
    output+="$(border_bottom "$BRIGHT_MAGENTA")\n"
    
    # 화면 클리어 후 출력
    clear
    echo -e "$output"
}

# 메인 루프
run_dashboard() {
    tput civis 2>/dev/null
    trap 'tput cnorm 2>/dev/null; clear; echo "대시보드 종료"; exit 0' INT TERM
    
    fetch_all_data
    
    while true; do
        fetch_all_data
        draw_screen
        sleep 1
    done
}

# 도움말
show_help() {
    echo "사용법: ./dashboard.sh [옵션]"
    echo ""
    echo "옵션:"
    echo "  -s, --server   서버 주소 (기본: http://localhost:8080)"
    echo "  -h, --help     도움말"
}

# 인자 처리
while [[ $# -gt 0 ]]; do
    case $1 in
        -s|--server) API_SERVER="$2"; shift 2 ;;
        -h|--help) show_help; exit 0 ;;
        *) echo "알 수 없는 옵션: $1"; exit 1 ;;
    esac
done

run_dashboard
