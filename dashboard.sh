#!/bin/bash

#############################################
# MyAPI 실시간 대시보드
#############################################

API_SERVER="${API_SERVER:-http://localhost:8080}"

# 색상
RESET='\033[0m'
BOLD='\033[1m'
DIM='\033[2m'
BRIGHT_RED='\033[91m'
BRIGHT_GREEN='\033[92m'
BRIGHT_YELLOW='\033[93m'
BRIGHT_BLUE='\033[94m'
BRIGHT_MAGENTA='\033[95m'
BRIGHT_CYAN='\033[96m'
CYAN='\033[36m'
WHITE='\033[37m'

# 캐시
CACHE_LOCATION=""
CACHE_STOCKS=""
CACHE_WEATHER=""
CACHE_SYSTEM=""
CACHE_TIME=0
CACHE_INTERVAL=60
CAT_FRAME=0
COLS=80

get_size() {
    COLS=$(tput cols 2>/dev/null || echo 80)
    [ $COLS -lt 70 ] && COLS=70
}

rep() { printf "%${2}s" | tr ' ' "$1"; }

need_refresh() { [ $(($(date +%s) - CACHE_TIME)) -ge $CACHE_INTERVAL ]; }

fetch_data() {
    if need_refresh; then
        CACHE_LOCATION=$(curl -s --connect-timeout 2 "${API_SERVER}/api/location/weather" 2>/dev/null)
        local symbols=("SPY" "QLD" "NVDA" "TSLA" "SNPS" "REKR" "SMCX" "ETHU" "BITX" "GLDM" "XXRP" "SOLT")
        CACHE_STOCKS=""
        for s in "${symbols[@]}"; do
            CACHE_STOCKS+="${s}|$(curl -s --connect-timeout 2 "${API_SERVER}/api/finnhub/quote?symbol=${s}" 2>/dev/null);"
        done
        CACHE_WEATHER=$(curl -s --connect-timeout 2 "${API_SERVER}/api/weather" 2>/dev/null)
        CACHE_TIME=$(date +%s)
    fi
    CACHE_SYSTEM=$(curl -s --connect-timeout 1 "${API_SERVER}/api/system/status" 2>/dev/null)
}

# 고정 너비 라인 출력
pline() {
    local text="$1"
    local color="${2:-$DIM}"
    local plain=$(echo -e "$text" | sed 's/\x1b\[[0-9;]*m//g')
    local pad=$((COLS - ${#plain} - 2))
    [ $pad -lt 0 ] && pad=0
    printf "${color}|${RESET}%-s%${pad}s${color}|${RESET}\n" "$text" ""
}

cline() {
    local text="$1"
    local color="${2:-$DIM}"
    local plain=$(echo -e "$text" | sed 's/\x1b\[[0-9;]*m//g')
    local total=$((COLS - 2))
    local tlen=${#plain}
    local lpad=$(( (total - tlen) / 2 ))
    local rpad=$((total - tlen - lpad))
    [ $lpad -lt 0 ] && lpad=0
    [ $rpad -lt 0 ] && rpad=0
    printf "${color}|${RESET}%${lpad}s%s%${rpad}s${color}|${RESET}\n" "" "$text" ""
}

hline() {
    local color="${1:-$DIM}"
    printf "${color}+%s+${RESET}\n" "$(rep '-' $((COLS-2)))"
}

draw() {
    get_size
    local refresh=$((CACHE_INTERVAL - ($(date +%s) - CACHE_TIME)))
    [ $refresh -lt 0 ] && refresh=0
    
    local cat_face
    case $((CAT_FRAME % 4)) in
        0) cat_face="(=^.^=)" ;; 1) cat_face="(=o.o=)" ;;
        2) cat_face="(=-.-=)" ;; 3) cat_face="(=^o^=)" ;;
    esac
    CAT_FRAME=$((CAT_FRAME + 1))
    
    # 버퍼 생성
    local B=""
    
    # 헤더
    B+="$(hline $BRIGHT_MAGENTA)\n"
    B+="$(cline "${BRIGHT_YELLOW}${cat_face}${RESET} ${BOLD}MyAPI 대시보드${RESET} ${BRIGHT_YELLOW}${cat_face}${RESET}" $BRIGHT_MAGENTA)\n"
    B+="$(cline "${DIM}$(date '+%Y-%m-%d %H:%M:%S') | 갱신: ${refresh}초${RESET}" $BRIGHT_MAGENTA)\n"
    B+="$(hline $BRIGHT_MAGENTA)\n"
    
    # 위치
    local loc="로딩..."
    [ -n "$CACHE_LOCATION" ] && [[ "$CACHE_LOCATION" == *"weather"* ]] && \
        loc=$(echo "$CACHE_LOCATION" | python3 -c "import sys,json;print(json.load(sys.stdin).get('weather','N/A'))" 2>/dev/null)
    B+="$(cline "${WHITE}@ ${loc}${RESET}" $DIM)\n"
    
    # 주식
    B+="$(hline $BRIGHT_YELLOW)\n"
    B+="$(cline "${BOLD}${BRIGHT_YELLOW}[ 미국 주식 정보 ]${RESET}" $BRIGHT_YELLOW)\n"
    B+="$(hline $BRIGHT_YELLOW)\n"
    
    local syms=("SPY" "QLD" "NVDA" "TSLA" "SNPS" "REKR" "SMCX" "ETHU" "BITX" "GLDM" "XXRP" "SOLT")
    local nms=("S&P500" "나스닥2X" "엔비디아" "테슬라" "시놉시스" "레코르" "SMC" "이더2X" "비트2X" "금ETF" "리플ETF" "솔라나")
    
    for i in "${!syms[@]}"; do
        local s="${syms[$i]}" n="${nms[$i]}"
        local raw=$(echo "$CACHE_STOCKS" | grep -o "${s}|[^;]*" | cut -d'|' -f2)
        local p="---" c="+0.00" pt="+0.00" clr="$WHITE"
        if [ -n "$raw" ] && [[ "$raw" == *"{"* ]]; then
            read p c pt <<< $(echo "$raw" | python3 -c "import sys,json
try:
    d=json.load(sys.stdin);print(f\"{d.get('c',0):.2f} {d.get('d',0):+.2f} {d.get('dp',0):+.2f}\")
except:print('0 0 0')" 2>/dev/null)
            [[ "$c" == +* ]] && [ "$c" != "+0.00" ] && clr="$BRIGHT_GREEN"
            [[ "$c" == -* ]] && clr="$BRIGHT_RED"
        fi
        B+="$(pline " ${CYAN}${s}${RESET} ${n} ${clr}\$${p} ${c} (${pt}%)${RESET}" $BRIGHT_YELLOW)\n"
    done
    
    # 날씨
    B+="$(hline $BRIGHT_BLUE)\n"
    B+="$(cline "${BOLD}${BRIGHT_BLUE}[ 한국 날씨 ]${RESET}" $BRIGHT_BLUE)\n"
    B+="$(hline $BRIGHT_BLUE)\n"
    
    if [ -n "$CACHE_WEATHER" ] && [[ "$CACHE_WEATHER" == *"["* ]]; then
        while IFS= read -r wl; do
            B+="$(pline " $wl" $BRIGHT_BLUE)\n"
        done <<< "$(echo "$CACHE_WEATHER" | python3 -c "import sys,json
data=json.load(sys.stdin)
def ic(w):
    w=(w or '').lower()
    if 'clear' in w:return '*'
    if 'cloud' in w:return '#'
    if 'rain' in w:return '~'
    if 'snow' in w:return 'o'
    if 'mist' in w or 'fog' in w or 'haze' in w:return '='
    return '-'
def tc(t):
    if t<=0:return '\033[96m'
    if t<=10:return '\033[94m'
    if t<=20:return '\033[92m'
    if t<=30:return '\033[93m'
    return '\033[91m'
r,b,d='\033[0m','\033[1m','\033[2m'
for c in data:
    print(f\"[{ic(c.get('weather',''))}] {b}{c.get('cityKo','?'):3}{r} {tc(c.get('temperatureCelsius',0))}{c.get('temperatureCelsius',0):5.1f}C{r} {d}{c.get('humidity',0):2}%{r}\")" 2>/dev/null)"
    fi
    
    # 시스템
    B+="$(hline $BRIGHT_GREEN)\n"
    B+="$(cline "${BOLD}${BRIGHT_GREEN}[ 시스템 상태 ]${RESET}" $BRIGHT_GREEN)\n"
    B+="$(hline $BRIGHT_GREEN)\n"
    
    if [ -n "$CACHE_SYSTEM" ] && [[ "$CACHE_SYSTEM" == *"{"* ]]; then
        while IFS= read -r sl; do
            B+="$(pline " $sl" $BRIGHT_GREEN)\n"
        done <<< "$(echo "$CACHE_SYSTEM" | python3 -c "import sys,json
d=json.load(sys.stdin)
def fm(b):
    for u in['B','K','M','G']:
        if b<1024:return f'{b:.0f}{u}'
        b/=1024
    return f'{b:.0f}T'
def br(p,w=12):
    f=int(p/100*w);g,y,r,s='\033[92m','\033[93m','\033[91m','\033[0m'
    return (g if p<60 else y if p<80 else r)+'#'*f+'-'*(w-f)+s
b,s='\033[1m','\033[0m'
cpu,pc=max(0,d.get('systemCpuLoad',0)),max(0,d.get('processCpuLoad',0))
mm,mu,mt=d.get('memoryUsagePercent',0),d.get('usedPhysicalMemory',0),d.get('totalPhysicalMemory',0)
hp,hu,hm=d.get('heapUsagePercent',0),d.get('heapUsed',0),d.get('heapMax',0)
th,gc,gt=d.get('threadCount',0),d.get('gcCount',0),d.get('gcTime',0)
up=d.get('uptimeMillis',0)//1000
print(f'{b}CPU{s} {cpu:5.1f}% {br(cpu)} {b}PROC{s} {pc:4.1f}% {br(pc)}')
print(f'{b}MEM{s} {mm:5.1f}% {br(mm)} {fm(mu)}/{fm(mt)}')
print(f'{b}HEAP{s}{hp:5.1f}% {br(hp)} {fm(hu)}/{fm(hm)}')
print(f'{b}THR{s} {th} {b}GC{s} {gc}/{gt}ms {b}UP{s} {up//3600}h{(up%3600)//60}m{up%60}s')" 2>/dev/null)"
    else
        B+="$(pline " 로딩..." $BRIGHT_GREEN)\n"
    fi
    
    # 푸터
    B+="$(hline $BRIGHT_MAGENTA)\n"
    B+="$(cline "${DIM}서버: ${API_SERVER} | 종료: Ctrl+C${RESET}" $BRIGHT_MAGENTA)\n"
    B+="$(hline $BRIGHT_MAGENTA)\n"
    
    # 출력 (커서 이동 + 버퍼 출력 + 나머지 클리어)
    printf '\033[H'
    printf '%b' "$B"
    printf '\033[J'
}

main() {
    printf '\033[2J\033[H'
    tput civis 2>/dev/null
    trap 'tput cnorm 2>/dev/null; printf "\033[2J\033[H"; echo "종료!"; exit 0' INT TERM
    fetch_data
    while true; do fetch_data; draw; sleep 1; done
}

[[ "$1" == "-h" ]] || [[ "$1" == "--help" ]] && { echo "사용법: $0 [-s 서버]"; exit 0; }
while [[ $# -gt 0 ]]; do case $1 in -s|--server) API_SERVER="$2"; shift 2;; *) shift;; esac; done
main
