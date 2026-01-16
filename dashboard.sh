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

# 캐시 변수
CACHE_LOCATION=""
CACHE_STOCKS=""
CACHE_WEATHER=""
CACHE_SYSTEM=""
CACHE_TIME=0
CACHE_INTERVAL=60

# 고양이 애니메이션 프레임
CAT_FRAME=0
declare -a CAT_FRAMES
CAT_FRAMES[0]='  /\_/\   ~nyaong~
 ( o.o )  
  > ^ <  ==========='
CAT_FRAMES[1]='  /\_/\   ~nyaong~
 ( -.- )  zzZ
  > ^ <  ==========='
CAT_FRAMES[2]='   /\_/\  
  ( o.o ) )
 ~(  ^ )~ ==========='
CAT_FRAMES[3]='    /\_/\ 
   ( ^.^ )
  ~(> < )~==========='

# 터미널 크기
get_terminal_size() {
    TERM_WIDTH=$(tput cols)
    TERM_HEIGHT=$(tput lines)
    [ $TERM_WIDTH -lt 80 ] && TERM_WIDTH=80
}

# 반복 문자 출력
repeat_char() {
    local char="$1"
    local count="$2"
    printf "%${count}s" | tr ' ' "$char"
}

# 중앙 정렬
center_text() {
    local text="$1"
    local width="$2"
    local plain=$(echo -e "$text" | sed 's/\x1b\[[0-9;]*m//g')
    local len=${#plain}
    local pad=$(( (width - len) / 2 ))
    [ $pad -lt 0 ] && pad=0
    printf "%${pad}s%s" "" "$text"
}

# 전체 너비 라인
full_line() {
    local char="${1:-─}"
    local color="${2:-$DIM}"
    echo -e "${color}$(repeat_char "$char" $TERM_WIDTH)${RESET}"
}

# 데이터 갱신 필요 여부
need_refresh() {
    local now=$(date +%s)
    [ $((now - CACHE_TIME)) -ge $CACHE_INTERVAL ]
}

# 데이터 가져오기
fetch_all_data() {
    if need_refresh; then
        CACHE_LOCATION=$(curl -s --connect-timeout 2 "${API_SERVER}/api/location/weather" 2>/dev/null)
        
        local symbols=("SPY" "QQQ" "NVDA" "SNPS" "REKR" "SMCX")
        CACHE_STOCKS=""
        for symbol in "${symbols[@]}"; do
            local data=$(curl -s --connect-timeout 2 "${API_SERVER}/api/finnhub/quote?symbol=${symbol}" 2>/dev/null)
            CACHE_STOCKS="${CACHE_STOCKS}${symbol}|${data};"
        done
        
        CACHE_WEATHER=$(curl -s --connect-timeout 2 "${API_SERVER}/api/weather" 2>/dev/null)
        CACHE_SYSTEM=$(curl -s --connect-timeout 2 "${API_SERVER}/api/system/status" 2>/dev/null)
        CACHE_TIME=$(date +%s)
    fi
}

# 화면 그리기 (버퍼 사용)
draw_screen() {
    get_terminal_size
    local output=""
    local next_refresh=$((CACHE_INTERVAL - ($(date +%s) - CACHE_TIME)))
    [ $next_refresh -lt 0 ] && next_refresh=0
    
    # 고양이 프레임 업데이트
    CAT_FRAME=$(( (CAT_FRAME + 1) % 4 ))
    
    # === 헤더 ===
    output+="\n"
    output+="${BRIGHT_CYAN}$(full_line '═')${RESET}\n"
    output+="$(center_text "${BOLD}${BRIGHT_MAGENTA}  🐱 MyAPI 실시간 대시보드 | $(date '+%Y-%m-%d %H:%M:%S') | 갱신: ${next_refresh}초  ${RESET}" $TERM_WIDTH)\n"
    output+="${BRIGHT_CYAN}$(full_line '═')${RESET}\n"
    
    # === 위치 날씨 ===
    local loc_weather="정보 없음"
    if [ -n "$CACHE_LOCATION" ] && [[ "$CACHE_LOCATION" == *"weather"* ]]; then
        loc_weather=$(echo "$CACHE_LOCATION" | python3 -c "import sys,json;d=json.load(sys.stdin);print(d.get('weather',''))" 2>/dev/null)
    fi
    output+="\n"
    output+="$(center_text "${BOLD}${WHITE}📍 ${loc_weather}${RESET}" $TERM_WIDTH)\n"
    
    # === 주식 시세 ===
    output+="\n"
    output+="${BRIGHT_YELLOW}$(full_line '─')${RESET}\n"
    output+="$(center_text "${BOLD}${BRIGHT_YELLOW}📈 미국 주식 시세${RESET}" $TERM_WIDTH)\n"
    output+="${BRIGHT_YELLOW}$(full_line '─')${RESET}\n"
    output+="\n"
    
    local symbols=("SPY" "QQQ" "NVDA" "SNPS" "REKR" "SMCX")
    local names=("S&P500 ETF  " "나스닥100    " "엔비디아     " "시놉시스     " "Rekor       " "SMC Corp    ")
    
    local stock_line=""
    local count=0
    local cols=3
    [ $TERM_WIDTH -lt 100 ] && cols=2
    
    for i in "${!symbols[@]}"; do
        local sym="${symbols[$i]}"
        local name="${names[$i]}"
        local raw=$(echo "$CACHE_STOCKS" | grep -o "${sym}|[^;]*" | cut -d'|' -f2)
        
        local price="---" change="0" pct="0" color="$WHITE" arrow="─"
        if [ -n "$raw" ] && [[ "$raw" == *"{"* ]]; then
            read price change pct <<< $(echo "$raw" | python3 -c "
import sys,json
d=json.load(sys.stdin)
p=d.get('c',0) or 0
c=d.get('d',0) or 0
dp=d.get('dp',0) or 0
print(f'{p:.2f} {c:.2f} {dp:.2f}')
" 2>/dev/null)
            if (( $(echo "$change > 0" | bc -l 2>/dev/null || echo 0) )); then
                color="$BRIGHT_GREEN"; arrow="▲"
            elif (( $(echo "$change < 0" | bc -l 2>/dev/null || echo 0) )); then
                color="$BRIGHT_RED"; arrow="▼"
            fi
        fi
        
        local item=$(printf "  ${BOLD}${CYAN}%-5s${RESET} %-10s ${color}\$%-7s %s%-5s (%s%%)${RESET}" "$sym" "$name" "$price" "$arrow" "$change" "$pct")
        stock_line+="$item"
        count=$((count + 1))
        
        if [ $((count % cols)) -eq 0 ]; then
            output+="$stock_line\n"
            stock_line=""
        fi
    done
    [ -n "$stock_line" ] && output+="$stock_line\n"
    
    # === 날씨 ===
    output+="\n"
    output+="${BRIGHT_BLUE}$(full_line '─')${RESET}\n"
    output+="$(center_text "${BOLD}${BRIGHT_BLUE}🌤️ 한국 주요 도시 날씨${RESET}" $TERM_WIDTH)\n"
    output+="${BRIGHT_BLUE}$(full_line '─')${RESET}\n"
    output+="\n"
    
    if [ -n "$CACHE_WEATHER" ] && [[ "$CACHE_WEATHER" == *"["* ]]; then
        local weather_out=$(echo "$CACHE_WEATHER" | python3 -c "
import sys,json
cols=$((TERM_WIDTH / 26))
if cols < 2: cols = 2
if cols > 5: cols = 5
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
out=''
for i,c in enumerate(data):
    nm=c.get('cityKo','')[:4]
    t=c.get('temperatureCelsius',0)
    w=c.get('weather','')
    h=c.get('humidity',0)
    out+=f'  {icon(w)} {b}{nm:4}{r} {tcolor(t)}{t:5.1f}°C{r} {d}({h}%){r}'
    if (i+1)%cols==0: out+='\n'
print(out)
" 2>/dev/null)
        output+="$weather_out\n"
    fi
    
    # === 시스템 상태 ===
    output+="\n"
    output+="${BRIGHT_GREEN}$(full_line '─')${RESET}\n"
    output+="$(center_text "${BOLD}${BRIGHT_GREEN}💻 서버 시스템 상태${RESET}" $TERM_WIDTH)\n"
    output+="${BRIGHT_GREEN}$(full_line '─')${RESET}\n"
    output+="\n"
    
    if [ -n "$CACHE_SYSTEM" ] && [[ "$CACHE_SYSTEM" == *"{"* ]]; then
        local sys_out=$(echo "$CACHE_SYSTEM" | python3 -c "
import sys,json
d=json.load(sys.stdin)
def fmt_bytes(b):
    for u in ['B','KB','MB','GB','TB']:
        if b<1024: return f'{b:.1f}{u}'
        b/=1024
    return f'{b:.1f}PB'
cpu=d.get('systemCpuLoad',-1)
mem_pct=d.get('memoryUsagePercent',0)
mem_used=d.get('usedPhysicalMemory',0)
mem_total=d.get('totalPhysicalMemory',0)
heap_pct=d.get('heapUsagePercent',0)
heap_used=d.get('heapUsed',0)
heap_max=d.get('heapMax',0)
threads=d.get('threadCount',0)
gc_count=d.get('gcCount',0)
gc_time=d.get('gcTime',0)
uptime=d.get('uptimeMillis',0)//1000
up_h,up_m,up_s=uptime//3600,(uptime%3600)//60,uptime%60
g='\033[92m'
y='\033[93m'
r='\033[91m'
c='\033[96m'
rs='\033[0m'
b='\033[1m'
def bar(pct,w=15):
    filled=int(pct/100*w)
    col=g if pct<60 else y if pct<80 else r
    return f'{col}'+('█'*filled)+('░'*(w-filled))+f'{rs}'
print(f'  {b}CPU:{rs} {cpu:5.1f}% {bar(cpu)}   {b}메모리:{rs} {mem_pct:5.1f}% {bar(mem_pct)} ({fmt_bytes(mem_used)}/{fmt_bytes(mem_total)})')
print(f'  {b}Heap:{rs}{heap_pct:5.1f}% {bar(heap_pct)} ({fmt_bytes(heap_used)}/{fmt_bytes(heap_max)})   {b}스레드:{rs} {threads}   {b}GC:{rs} {gc_count}회/{gc_time}ms')
print(f'  {b}Uptime:{rs} {up_h}시간 {up_m}분 {up_s}초')
" 2>/dev/null)
        output+="$sys_out\n"
    else
        output+="  시스템 정보 로딩 중...\n"
    fi
    
    # === 푸터 ===
    output+="\n"
    output+="${DIM}$(full_line '─')${RESET}\n"
    output+="$(center_text "${DIM}서버: ${API_SERVER} │ Ctrl+C: 종료${RESET}" $TERM_WIDTH)\n"
    
    # 화면 출력 (깜빡임 방지)
    clear
    echo -e "$output"
}

# 메인 루프
run_dashboard() {
    tput civis  # 커서 숨기기
    trap 'tput cnorm; clear; echo "대시보드 종료"; exit 0' INT TERM
    
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
