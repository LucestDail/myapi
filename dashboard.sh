#!/bin/bash

#############################################
# MyAPI 실시간 대시보드
#############################################

API_SERVER="${API_SERVER:-http://localhost:8080}"

# 색상
RESET='\033[0m'
BOLD='\033[1m'
DIM='\033[2m'
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

# 캐시
CACHE_LOCATION=""
CACHE_STOCKS=""
CACHE_WEATHER=""
CACHE_SYSTEM=""
CACHE_TIME=0
CACHE_INTERVAL=60

# 고양이 프레임
CAT_FRAME=0

# 터미널 크기
get_size() {
    COLS=$(tput cols 2>/dev/null || echo 80)
    [ $COLS -lt 60 ] && COLS=60
}

# 반복 문자
rep() { printf "%${2}s" | tr ' ' "$1"; }

# 라인 출력
line() {
    local color="${1:-$DIM}"
    echo -e "${color}+$(rep '-' $((COLS-2)))+${RESET}"
}

# 텍스트 출력 (테두리 포함)
txt() {
    local text="$1"
    local color="${2:-$DIM}"
    local plain=$(echo -e "$text" | sed 's/\x1b\[[0-9;]*m//g')
    local len=${#plain}
    local pad=$((COLS - len - 4))
    [ $pad -lt 0 ] && pad=0
    echo -e "${color}|${RESET} ${text}$(printf "%${pad}s" "") ${color}|${RESET}"
}

# 중앙 텍스트
center() {
    local text="$1"
    local color="${2:-$DIM}"
    local plain=$(echo -e "$text" | sed 's/\x1b\[[0-9;]*m//g')
    local len=${#plain}
    local total=$((COLS - 2))
    local left=$(( (total - len) / 2 ))
    local right=$((total - len - left))
    [ $left -lt 0 ] && left=0
    [ $right -lt 0 ] && right=0
    echo -e "${color}|${RESET}$(printf "%${left}s" "")${text}$(printf "%${right}s" "")${color}|${RESET}"
}

# 빈 줄
empty() {
    local color="${1:-$DIM}"
    echo -e "${color}|$(rep ' ' $((COLS-2)))|${RESET}"
}

# 데이터 갱신 체크
need_refresh() {
    [ $(($(date +%s) - CACHE_TIME)) -ge $CACHE_INTERVAL ]
}

# 데이터 가져오기
fetch_data() {
    if need_refresh; then
        CACHE_LOCATION=$(curl -s --connect-timeout 2 "${API_SERVER}/api/location/weather" 2>/dev/null)
        local symbols=("SPY" "QQQ" "NVDA" "TSLA" "SNPS" "REKR" "SMCX" "ETHU" "BITX" "GLDM")
        CACHE_STOCKS=""
        for s in "${symbols[@]}"; do
            local d=$(curl -s --connect-timeout 2 "${API_SERVER}/api/finnhub/quote?symbol=${s}" 2>/dev/null)
            CACHE_STOCKS="${CACHE_STOCKS}${s}|${d};"
        done
        CACHE_WEATHER=$(curl -s --connect-timeout 2 "${API_SERVER}/api/weather" 2>/dev/null)
        CACHE_TIME=$(date +%s)
    fi
    CACHE_SYSTEM=$(curl -s --connect-timeout 1 "${API_SERVER}/api/system/status" 2>/dev/null)
}

# 화면 그리기
draw() {
    get_size
    local refresh=$((CACHE_INTERVAL - ($(date +%s) - CACHE_TIME)))
    [ $refresh -lt 0 ] && refresh=0
    
    # 고양이 표정
    local cat_face
    case $((CAT_FRAME % 4)) in
        0) cat_face="(=^.^=)" ;;
        1) cat_face="(=o.o=)" ;;
        2) cat_face="(=-.-=)" ;;
        3) cat_face="(=^o^=)" ;;
    esac
    CAT_FRAME=$((CAT_FRAME + 1))
    
    clear
    
    # 헤더
    line "$BRIGHT_MAGENTA"
    center "${BRIGHT_YELLOW}${cat_face}${RESET} ${BOLD}MyAPI Dashboard${RESET} ${BRIGHT_YELLOW}${cat_face}${RESET}" "$BRIGHT_MAGENTA"
    center "${DIM}$(date '+%Y-%m-%d %H:%M:%S')${RESET}  |  ${BRIGHT_CYAN}Refresh: ${refresh}s${RESET}" "$BRIGHT_MAGENTA"
    line "$BRIGHT_MAGENTA"
    
    # 위치 날씨
    local loc="Loading..."
    if [ -n "$CACHE_LOCATION" ] && [[ "$CACHE_LOCATION" == *"weather"* ]]; then
        loc=$(echo "$CACHE_LOCATION" | python3 -c "import sys,json;print(json.load(sys.stdin).get('weather','N/A'))" 2>/dev/null)
    fi
    center "${WHITE}@ ${loc}${RESET}" "$DIM"
    line "$BRIGHT_YELLOW"
    
    # 주식
    center "${BOLD}${BRIGHT_YELLOW}[ Stock Prices ]${RESET}" "$BRIGHT_YELLOW"
    line "$BRIGHT_YELLOW"
    
    local symbols=("SPY" "QQQ" "NVDA" "TSLA" "SNPS" "REKR" "SMCX" "ETHU" "BITX" "GLDM")
    local names=("S&P500 ETF" "NASDAQ 100" "NVIDIA" "TESLA" "Synopsys" "Rekor" "SMC Corp" "Ether 2X" "Bitcoin 2X" "Gold ETF")
    
    for i in "${!symbols[@]}"; do
        local sym="${symbols[$i]}"
        local name="${names[$i]}"
        local raw=$(echo "$CACHE_STOCKS" | grep -o "${sym}|[^;]*" | cut -d'|' -f2)
        
        local price="---.--" chg="+0.00" pct="+0.00" color="$WHITE" arrow=" "
        if [ -n "$raw" ] && [[ "$raw" == *"{"* ]]; then
            read price chg pct <<< $(echo "$raw" | python3 -c "
import sys,json
try:
    d=json.load(sys.stdin)
    print(f\"{d.get('c',0):.2f} {d.get('d',0):+.2f} {d.get('dp',0):+.2f}\")
except: print('0 0 0')
" 2>/dev/null)
            if [[ "$chg" == +* ]] && [ "$chg" != "+0.00" ]; then
                color="$BRIGHT_GREEN"; arrow="+"
            elif [[ "$chg" == -* ]]; then
                color="$BRIGHT_RED"; arrow=""
            fi
        fi
        txt "$(printf "${CYAN}%-6s${RESET} %-10s ${color}\$%-9s %s%-7s (%s%%)${RESET}" "$sym" "$name" "$price" "$arrow" "$chg" "$pct")"
    done
    
    # 날씨
    line "$BRIGHT_BLUE"
    center "${BOLD}${BRIGHT_BLUE}[ Korea Weather ]${RESET}" "$BRIGHT_BLUE"
    line "$BRIGHT_BLUE"
    
    if [ -n "$CACHE_WEATHER" ] && [[ "$CACHE_WEATHER" == *"["* ]]; then
        echo "$CACHE_WEATHER" | python3 -c "
import sys,json
data=json.load(sys.stdin)
def tc(t):
    if t<=0: return '\033[96m'
    elif t<=10: return '\033[94m'
    elif t<=20: return '\033[92m'
    elif t<=30: return '\033[93m'
    return '\033[91m'
r='\033[0m'
b='\033[1m'
d='\033[2m'
for c in data:
    nm=c.get('cityKo','?')
    t=c.get('temperatureCelsius',0)
    w=c.get('weather','')[:5]
    h=c.get('humidity',0)
    print(f'{b}{nm:4}{r} {tc(t)}{t:5.1f}C{r} {d}{w:5} {h}%{r}')
" 2>/dev/null | while read wline; do
            txt "  $wline"
        done
    fi
    
    # 시스템
    line "$BRIGHT_GREEN"
    center "${BOLD}${BRIGHT_GREEN}[ System Status ]${RESET}" "$BRIGHT_GREEN"
    line "$BRIGHT_GREEN"
    
    if [ -n "$CACHE_SYSTEM" ] && [[ "$CACHE_SYSTEM" == *"{"* ]]; then
        echo "$CACHE_SYSTEM" | python3 -c "
import sys,json
d=json.load(sys.stdin)
def fmt(b):
    for u in ['B','KB','MB','GB']:
        if b<1024: return f'{b:.1f}{u}'
        b/=1024
    return f'{b:.1f}TB'
def bar(p,w=20):
    f=int(p/100*w)
    g,y,r,rs='\033[92m','\033[93m','\033[91m','\033[0m'
    c=g if p<60 else y if p<80 else r
    return c+'#'*f+'-'*(w-f)+rs
b='\033[1m'
rs='\033[0m'
cpu=max(0,d.get('systemCpuLoad',0))
pcpu=max(0,d.get('processCpuLoad',0))
mem=d.get('memoryUsagePercent',0)
mu=d.get('usedPhysicalMemory',0)
mt=d.get('totalPhysicalMemory',0)
hp=d.get('heapUsagePercent',0)
hu=d.get('heapUsed',0)
hm=d.get('heapMax',0)
th=d.get('threadCount',0)
pk=d.get('peakThreadCount',0)
gc=d.get('gcCount',0)
gt=d.get('gcTime',0)
cl=d.get('loadedClassCount',0)
up=d.get('uptimeMillis',0)//1000
print(f'{b}CPU{rs}     {cpu:5.1f}% {bar(cpu)}')
print(f'{b}Process{rs} {pcpu:5.1f}% {bar(pcpu)}')
print(f'{b}Memory{rs}  {mem:5.1f}% {bar(mem)} {fmt(mu)}/{fmt(mt)}')
print(f'{b}Heap{rs}    {hp:5.1f}% {bar(hp)} {fmt(hu)}/{fmt(hm)}')
print(f'{b}Thread{rs}  {th} (peak {pk})  {b}GC{rs} {gc}x/{gt}ms  {b}Class{rs} {cl}')
print(f'{b}Uptime{rs}  {up//3600}h {(up%3600)//60}m {up%60}s')
" 2>/dev/null | while read sline; do
            txt "  $sline"
        done
    else
        txt "  Loading system info..."
    fi
    
    # 푸터
    line "$BRIGHT_MAGENTA"
    center "${DIM}Server: ${API_SERVER}  |  Ctrl+C to exit${RESET}" "$BRIGHT_MAGENTA"
    line "$BRIGHT_MAGENTA"
}

# 메인
main() {
    tput civis 2>/dev/null
    trap 'tput cnorm 2>/dev/null; clear; echo "Bye!"; exit 0' INT TERM
    
    fetch_data
    while true; do
        fetch_data
        draw
        sleep 1
    done
}

# 도움말
if [[ "$1" == "-h" ]] || [[ "$1" == "--help" ]]; then
    echo "Usage: ./dashboard.sh [-s SERVER_URL]"
    echo "  -s, --server  API server (default: http://localhost:8080)"
    exit 0
fi

# 인자
while [[ $# -gt 0 ]]; do
    case $1 in
        -s|--server) API_SERVER="$2"; shift 2 ;;
        *) shift ;;
    esac
done

main
