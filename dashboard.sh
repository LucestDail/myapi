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
COLS=80

get_size() {
    COLS=$(tput cols 2>/dev/null || echo 80)
    [ $COLS -lt 60 ] && COLS=60
}

# 반복 문자
rep() { printf "%${2}s" | tr ' ' "$1"; }

# 데이터 갱신 체크
need_refresh() {
    [ $(($(date +%s) - CACHE_TIME)) -ge $CACHE_INTERVAL ]
}

# 데이터 가져오기
fetch_data() {
    if need_refresh; then
        CACHE_LOCATION=$(curl -s --connect-timeout 2 "${API_SERVER}/api/location/weather" 2>/dev/null)
        # 주식 티커 (12개)
        local symbols=("SPY" "QLD" "NVDA" "TSLA" "SNPS" "REKR" "SMCX" "ETHU" "BITX" "GLDM" "XXRP" "SOLT")
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

# 화면 그리기 (한번에 출력)
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
    
    # 전체 화면을 하나의 문자열로 생성
    local screen=""
    local line_sep="+$(rep '-' $((COLS-2)))+"
    
    # 헤더
    screen+="${BRIGHT_MAGENTA}${line_sep}${RESET}\n"
    
    local title="${BRIGHT_YELLOW}${cat_face}${RESET} ${BOLD}MyAPI 대시보드${RESET} ${BRIGHT_YELLOW}${cat_face}${RESET}"
    local title_plain="${cat_face} MyAPI 대시보드 ${cat_face}"
    local title_pad=$(( (COLS - 2 - ${#title_plain} - 4) / 2 ))
    screen+="${BRIGHT_MAGENTA}|${RESET}$(rep ' ' $title_pad)${title}$(rep ' ' $((COLS - 2 - title_pad - ${#title_plain} - 4)))${BRIGHT_MAGENTA}|${RESET}\n"
    
    local time_str="$(date '+%Y-%m-%d %H:%M:%S')  |  갱신: ${refresh}초"
    local time_pad=$(( (COLS - 2 - ${#time_str} - 2) / 2 ))
    screen+="${BRIGHT_MAGENTA}|${RESET}$(rep ' ' $time_pad)${DIM}${time_str}${RESET}$(rep ' ' $((COLS - 2 - time_pad - ${#time_str} - 2)))${BRIGHT_MAGENTA}|${RESET}\n"
    
    screen+="${BRIGHT_MAGENTA}${line_sep}${RESET}\n"
    
    # 위치 날씨
    local loc="로딩중..."
    if [ -n "$CACHE_LOCATION" ] && [[ "$CACHE_LOCATION" == *"weather"* ]]; then
        loc=$(echo "$CACHE_LOCATION" | python3 -c "import sys,json;print(json.load(sys.stdin).get('weather','N/A'))" 2>/dev/null)
    fi
    local loc_text="@ ${loc}"
    local loc_pad=$(( (COLS - 2 - ${#loc_text}) / 2 ))
    screen+="${DIM}|${RESET}$(rep ' ' $loc_pad)${WHITE}${loc_text}${RESET}$(rep ' ' $((COLS - 2 - loc_pad - ${#loc_text})))${DIM}|${RESET}\n"
    
    # 주식 섹션
    screen+="${BRIGHT_YELLOW}${line_sep}${RESET}\n"
    local stock_title="[ 미국 주식 정보 ]"
    local stock_pad=$(( (COLS - 2 - ${#stock_title} - 6) / 2 ))
    screen+="${BRIGHT_YELLOW}|${RESET}$(rep ' ' $stock_pad)${BOLD}${BRIGHT_YELLOW}${stock_title}${RESET}$(rep ' ' $((COLS - 2 - stock_pad - ${#stock_title} - 6)))${BRIGHT_YELLOW}|${RESET}\n"
    screen+="${BRIGHT_YELLOW}${line_sep}${RESET}\n"
    
    # 주식 티커 및 한글명 (12개)
    local symbols=("SPY" "QLD" "NVDA" "TSLA" "SNPS" "REKR" "SMCX" "ETHU" "BITX" "GLDM" "XXRP" "SOLT")
    local names=("S&P500 ETF" "나스닥 2배" "엔비디아" "테슬라" "시놉시스" "레코르" "SMC 코프" "이더리움2배" "비트코인2배" "금 ETF" "리플 ETF" "솔라나 ETF")
    
    for i in "${!symbols[@]}"; do
        local sym="${symbols[$i]}"
        local name="${names[$i]}"
        local raw=$(echo "$CACHE_STOCKS" | grep -o "${sym}|[^;]*" | cut -d'|' -f2)
        
        local price="---.--" chg="+0.00" pct="+0.00" color="$WHITE"
        if [ -n "$raw" ] && [[ "$raw" == *"{"* ]]; then
            read price chg pct <<< $(echo "$raw" | python3 -c "
import sys,json
try:
    d=json.load(sys.stdin)
    print(f\"{d.get('c',0):.2f} {d.get('d',0):+.2f} {d.get('dp',0):+.2f}\")
except: print('0 0 0')
" 2>/dev/null)
            if [[ "$chg" == +* ]] && [ "$chg" != "+0.00" ]; then
                color="$BRIGHT_GREEN"
            elif [[ "$chg" == -* ]]; then
                color="$BRIGHT_RED"
            fi
        fi
        local stock_line=$(printf " ${CYAN}%-5s${RESET} %-10s ${color}\$%-8s %+-7s (%s%%)${RESET}" "$sym" "$name" "$price" "$chg" "$pct")
        local stock_plain=$(printf " %-5s %-10s \$%-8s %+-7s (%s%%)" "$sym" "$name" "$price" "$chg" "$pct")
        local stock_rpad=$((COLS - 3 - ${#stock_plain} - 6))
        [ $stock_rpad -lt 0 ] && stock_rpad=0
        screen+="${BRIGHT_YELLOW}|${RESET}${stock_line}$(rep ' ' $stock_rpad)${BRIGHT_YELLOW}|${RESET}\n"
    done
    
    # 날씨 섹션
    screen+="${BRIGHT_BLUE}${line_sep}${RESET}\n"
    local weather_title="[ 한국 날씨 ]"
    local weather_pad=$(( (COLS - 2 - ${#weather_title} - 4) / 2 ))
    screen+="${BRIGHT_BLUE}|${RESET}$(rep ' ' $weather_pad)${BOLD}${BRIGHT_BLUE}${weather_title}${RESET}$(rep ' ' $((COLS - 2 - weather_pad - ${#weather_title} - 4)))${BRIGHT_BLUE}|${RESET}\n"
    screen+="${BRIGHT_BLUE}${line_sep}${RESET}\n"
    
    if [ -n "$CACHE_WEATHER" ] && [[ "$CACHE_WEATHER" == *"["* ]]; then
        local weather_lines=$(echo "$CACHE_WEATHER" | python3 -c "
import sys,json
data=json.load(sys.stdin)
def icon(w):
    w=w.lower() if w else ''
    if 'clear' in w: return '*'
    elif 'cloud' in w: return '#'
    elif 'rain' in w or 'drizzle' in w: return '~'
    elif 'snow' in w: return 'o'
    elif 'mist' in w or 'fog' in w or 'haze' in w: return '='
    elif 'thunder' in w: return '!'
    return '-'
def tc(t):
    if t<=0: return '\033[96m'
    elif t<=10: return '\033[94m'
    elif t<=20: return '\033[92m'
    elif t<=30: return '\033[93m'
    return '\033[91m'
r='\033[0m'
b='\033[1m'
d='\033[2m'
y='\033[93m'
for c in data:
    nm=c.get('cityKo','?')
    t=c.get('temperatureCelsius',0)
    w=c.get('weather','')
    h=c.get('humidity',0)
    ic=icon(w)
    print(f' {y}[{ic}]{r} {b}{nm:4}{r} {tc(t)}{t:5.1f}C{r} {d}습도{h:2}%{r}')
" 2>/dev/null)
        while IFS= read -r wline; do
            local wplain=$(echo -e "$wline" | sed 's/\x1b\[[0-9;]*m//g')
            local wrpad=$((COLS - 3 - ${#wplain}))
            [ $wrpad -lt 0 ] && wrpad=0
            screen+="${BRIGHT_BLUE}|${RESET}${wline}$(rep ' ' $wrpad)${BRIGHT_BLUE}|${RESET}\n"
        done <<< "$weather_lines"
    fi
    
    # 시스템 섹션
    screen+="${BRIGHT_GREEN}${line_sep}${RESET}\n"
    local sys_title="[ 시스템 상태 ]"
    local sys_pad=$(( (COLS - 2 - ${#sys_title} - 5) / 2 ))
    screen+="${BRIGHT_GREEN}|${RESET}$(rep ' ' $sys_pad)${BOLD}${BRIGHT_GREEN}${sys_title}${RESET}$(rep ' ' $((COLS - 2 - sys_pad - ${#sys_title} - 5)))${BRIGHT_GREEN}|${RESET}\n"
    screen+="${BRIGHT_GREEN}${line_sep}${RESET}\n"
    
    if [ -n "$CACHE_SYSTEM" ] && [[ "$CACHE_SYSTEM" == *"{"* ]]; then
        local sys_lines=$(echo "$CACHE_SYSTEM" | python3 -c "
import sys,json
d=json.load(sys.stdin)
def fmt(b):
    for u in ['B','KB','MB','GB']:
        if b<1024: return f'{b:.1f}{u}'
        b/=1024
    return f'{b:.1f}TB'
def bar(p,w=15):
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
gc=d.get('gcCount',0)
gt=d.get('gcTime',0)
up=d.get('uptimeMillis',0)//1000
print(f' {b}CPU{rs}      {cpu:5.1f}% {bar(cpu)}')
print(f' {b}프로세스{rs}  {pcpu:5.1f}% {bar(pcpu)}')
print(f' {b}메모리{rs}    {mem:5.1f}% {bar(mem)} {fmt(mu)}/{fmt(mt)}')
print(f' {b}힙{rs}        {hp:5.1f}% {bar(hp)} {fmt(hu)}/{fmt(hm)}')
print(f' {b}스레드{rs} {th}  {b}GC{rs} {gc}회/{gt}ms  {b}업타임{rs} {up//3600}시간{(up%3600)//60}분{up%60}초')
" 2>/dev/null)
        while IFS= read -r sline; do
            local splain=$(echo -e "$sline" | sed 's/\x1b\[[0-9;]*m//g')
            local srpad=$((COLS - 3 - ${#splain} + 5))
            [ $srpad -lt 0 ] && srpad=0
            screen+="${BRIGHT_GREEN}|${RESET}${sline}$(rep ' ' $srpad)${BRIGHT_GREEN}|${RESET}\n"
        done <<< "$sys_lines"
    else
        screen+="${BRIGHT_GREEN}|${RESET} 로딩중...$(rep ' ' $((COLS - 14)))${BRIGHT_GREEN}|${RESET}\n"
    fi
    
    # 푸터
    screen+="${BRIGHT_MAGENTA}${line_sep}${RESET}\n"
    local footer="서버: ${API_SERVER}  |  종료: Ctrl+C"
    local footer_pad=$(( (COLS - 2 - ${#footer}) / 2 ))
    screen+="${BRIGHT_MAGENTA}|${RESET}$(rep ' ' $footer_pad)${DIM}${footer}${RESET}$(rep ' ' $((COLS - 2 - footer_pad - ${#footer})))${BRIGHT_MAGENTA}|${RESET}\n"
    screen+="${BRIGHT_MAGENTA}${line_sep}${RESET}\n"
    
    # 커서를 맨 위로 이동 후 한번에 출력
    tput cup 0 0 2>/dev/null
    echo -e "$screen"
}

# 메인
main() {
    clear
    tput civis 2>/dev/null
    trap 'tput cnorm 2>/dev/null; clear; echo "종료!"; exit 0' INT TERM
    
    fetch_data
    while true; do
        fetch_data
        draw
        sleep 1
    done
}

# 도움말
if [[ "$1" == "-h" ]] || [[ "$1" == "--help" ]]; then
    echo "사용법: ./dashboard.sh [-s 서버주소]"
    echo "  -s, --server  API 서버 (기본: http://localhost:8080)"
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
