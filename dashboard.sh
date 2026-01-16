#!/bin/bash
#############################################
# MyAPI 대시보드
#############################################

API_SERVER="${API_SERVER:-http://localhost:8080}"

# 색상
RS='\033[0m'; BD='\033[1m'; DM='\033[2m'
RD='\033[91m'; GR='\033[92m'; YL='\033[93m'; BL='\033[94m'; MG='\033[95m'; CY='\033[96m'; WH='\033[37m'

# 캐시
C_LOC="" C_STK="" C_WTH="" C_SYS="" C_TM=0
CAT=0

# 터미널 너비
W=$(tput cols 2>/dev/null || echo 80)
[ $W -lt 60 ] && W=60

refresh_check() { [ $(($(date +%s) - C_TM)) -ge 60 ]; }

fetch() {
    if refresh_check; then
        C_LOC=$(curl -s -m2 "${API_SERVER}/api/location/weather" 2>/dev/null)
        C_STK=""
        for s in SPY QLD NVDA TSLA SNPS REKR SMCX ETHU BITX GLDM XXRP SOLT; do
            C_STK+="${s}|$(curl -s -m2 "${API_SERVER}/api/finnhub/quote?symbol=${s}" 2>/dev/null);"
        done
        C_WTH=$(curl -s -m2 "${API_SERVER}/api/weather" 2>/dev/null)
        C_TM=$(date +%s)
    fi
    C_SYS=$(curl -s -m1 "${API_SERVER}/api/system/status" 2>/dev/null)
}

draw() {
    W=$(tput cols 2>/dev/null || echo 80)
    [ $W -lt 60 ] && W=60
    
    local sec=$((60 - ($(date +%s) - C_TM)))
    [ $sec -lt 0 ] && sec=0
    
    local cat_arr=("(=^.^=)" "(=o.o=)" "(=-.-=)" "(=^o^=)")
    local cat="${cat_arr[$((CAT % 4))]}"
    CAT=$((CAT + 1))
    
    # Python으로 전체 화면 생성 (한글 너비 정확히 계산)
    python3 << PYEND
import json, sys

W = $W
sec = $sec
cat = "$cat"
api = "$API_SERVER"

# 캐시 데이터
loc_raw = '''$C_LOC'''
stk_raw = '''$C_STK'''
wth_raw = '''$C_WTH'''
sys_raw = '''$C_SYS'''

# 색상
RS, BD, DM = '\033[0m', '\033[1m', '\033[2m'
RD, GR, YL, BL, MG, CY, WH = '\033[91m', '\033[92m', '\033[93m', '\033[94m', '\033[95m', '\033[96m', '\033[37m'

def wlen(s):
    """문자열의 실제 표시 너비 (한글=2, 영어=1)"""
    s = ''.join(c for c in s if c not in '\033[0-9;m')
    import re
    s = re.sub(r'\033\[[0-9;]*m', '', s)
    w = 0
    for c in s:
        if '\uac00' <= c <= '\ud7a3' or '\u4e00' <= c <= '\u9fff':
            w += 2
        elif '\uff00' <= c <= '\uffef':
            w += 2
        else:
            w += 1
    return w

def hline(color=DM):
    return f"{color}+{'-'*(W-2)}+{RS}"

def pline(text, color=DM):
    tw = wlen(text)
    pad = W - 4 - tw
    if pad < 0: pad = 0
    return f"{color}|{RS} {text}{' '*pad} {color}|{RS}"

def cline(text, color=DM):
    tw = wlen(text)
    total = W - 2
    lp = (total - tw) // 2
    rp = total - tw - lp
    if lp < 0: lp = 0
    if rp < 0: rp = 0
    return f"{color}|{RS}{' '*lp}{text}{' '*rp}{color}|{RS}"

lines = []

# 헤더
lines.append(hline(MG))
lines.append(cline(f"{YL}{cat}{RS} {BD}MyAPI 대시보드{RS} {YL}{cat}{RS}", MG))
lines.append(cline(f"{DM}{__import__('datetime').datetime.now().strftime('%Y-%m-%d %H:%M:%S')} | 갱신: {sec}초{RS}", MG))
lines.append(hline(MG))

# 위치
loc = "로딩..."
try:
    d = json.loads(loc_raw)
    loc = d.get('weather', 'N/A')
except: pass
lines.append(cline(f"{WH}@ {loc}{RS}", DM))

# 주식
lines.append(hline(YL))
lines.append(cline(f"{BD}{YL}[ 미국 주식 정보 ]{RS}", YL))
lines.append(hline(YL))

stocks = [
    ("SPY", "S&P500"), ("QLD", "나스닥2X"), ("NVDA", "엔비디아"), ("TSLA", "테슬라"),
    ("SNPS", "시놉시스"), ("REKR", "레코르"), ("SMCX", "SMC"), ("ETHU", "이더2X"),
    ("BITX", "비트2X"), ("GLDM", "금ETF"), ("XXRP", "리플ETF"), ("SOLT", "솔라나")
]

for sym, nm in stocks:
    p, c, pt, clr = "---", "+0.00", "+0.00", WH
    try:
        for part in stk_raw.split(';'):
            if part.startswith(sym + '|'):
                raw = part.split('|', 1)[1]
                if raw and '{' in raw:
                    d = json.loads(raw)
                    p = f"{d.get('c', 0):.2f}"
                    c = f"{d.get('d', 0):+.2f}"
                    pt = f"{d.get('dp', 0):+.2f}"
                    if d.get('d', 0) > 0: clr = GR
                    elif d.get('d', 0) < 0: clr = RD
                break
    except: pass
    lines.append(pline(f"{CY}{sym:5}{RS} {nm:6} {clr}\${p:>8} {c:>7} ({pt}%){RS}", YL))

# 날씨
lines.append(hline(BL))
lines.append(cline(f"{BD}{BL}[ 한국 날씨 ]{RS}", BL))
lines.append(hline(BL))

try:
    data = json.loads(wth_raw)
    for c in data:
        nm = c.get('cityKo', '?')
        t = c.get('temperatureCelsius', 0)
        h = c.get('humidity', 0)
        w = (c.get('weather', '') or '').lower()
        ic = '*' if 'clear' in w else '#' if 'cloud' in w else '~' if 'rain' in w else '=' if any(x in w for x in ['mist','fog','haze']) else '-'
        tc = '\033[96m' if t <= 0 else '\033[94m' if t <= 10 else '\033[92m' if t <= 20 else '\033[93m' if t <= 30 else '\033[91m'
        lines.append(pline(f"[{ic}] {BD}{nm:4}{RS} {tc}{t:5.1f}C{RS} {DM}{h:2}%{RS}", BL))
except: 
    lines.append(pline("로딩...", BL))

# 시스템
lines.append(hline(GR))
lines.append(cline(f"{BD}{GR}[ 시스템 상태 ]{RS}", GR))
lines.append(hline(GR))

try:
    d = json.loads(sys_raw)
    def fm(b):
        for u in ['B','K','M','G']:
            if b < 1024: return f"{b:.0f}{u}"
            b /= 1024
        return f"{b:.0f}T"
    def bar(p, w=12):
        f = int(p / 100 * w)
        c = GR if p < 60 else YL if p < 80 else RD
        return c + '#' * f + '-' * (w - f) + RS
    
    cpu = max(0, d.get('systemCpuLoad', 0))
    pc = max(0, d.get('processCpuLoad', 0))
    mm = d.get('memoryUsagePercent', 0)
    mu, mt = d.get('usedPhysicalMemory', 0), d.get('totalPhysicalMemory', 0)
    hp = d.get('heapUsagePercent', 0)
    hu, hm = d.get('heapUsed', 0), d.get('heapMax', 0)
    th = d.get('threadCount', 0)
    gc, gt = d.get('gcCount', 0), d.get('gcTime', 0)
    up = d.get('uptimeMillis', 0) // 1000
    
    lines.append(pline(f"{BD}CPU{RS} {cpu:5.1f}% {bar(cpu)} {BD}PROC{RS} {pc:4.1f}% {bar(pc)}", GR))
    lines.append(pline(f"{BD}MEM{RS} {mm:5.1f}% {bar(mm)} {fm(mu)}/{fm(mt)}", GR))
    lines.append(pline(f"{BD}HEAP{RS}{hp:5.1f}% {bar(hp)} {fm(hu)}/{fm(hm)}", GR))
    lines.append(pline(f"{BD}THR{RS} {th} {BD}GC{RS} {gc}/{gt}ms {BD}UP{RS} {up//3600}h{(up%3600)//60}m{up%60}s", GR))
except:
    lines.append(pline("로딩...", GR))

# 푸터
lines.append(hline(MG))
lines.append(cline(f"{DM}서버: {api} | 종료: Ctrl+C{RS}", MG))
lines.append(hline(MG))

# 출력
print('\033[H', end='')
for l in lines:
    print(l)
print('\033[J', end='')
PYEND
}

main() {
    printf '\033[2J\033[H'
    tput civis 2>/dev/null
    trap 'tput cnorm 2>/dev/null; printf "\033[2J\033[H"; echo "종료!"; exit 0' INT TERM
    fetch
    while true; do fetch; draw; sleep 1; done
}

[[ "$1" == "-h" || "$1" == "--help" ]] && { echo "사용법: $0 [-s 서버]"; exit 0; }
while [[ $# -gt 0 ]]; do case $1 in -s|--server) API_SERVER="$2"; shift 2;; *) shift;; esac; done
main
