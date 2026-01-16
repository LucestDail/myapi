#!/bin/bash
#############################################
# MyAPI Dashboard
#############################################

API_SERVER="${API_SERVER:-http://localhost:8080}"

# Cache (stock/weather: 60s, news: 600s)
C_LOC="" C_STK="" C_WTH="" C_SYS="" C_RSS=""
C_TM=0 C_RSS_TM=0

fetch() {
    local now=$(date +%s)
    
    # Stock & Weather: refresh every 60s
    if [ $((now - C_TM)) -ge 60 ]; then
        C_LOC=$(curl -s -m2 "${API_SERVER}/api/location/weather" 2>/dev/null || echo '{"weather":"N/A"}')
        C_STK=""
        for s in SPY QLD NVDA TSLA SNPS REKR SMCX ETHU BITX GLDM XXRP SOLT; do
            local r=$(curl -s -m2 "${API_SERVER}/api/finnhub/quote?symbol=${s}" 2>/dev/null || echo '{}')
            C_STK+="${s}|${r};"
        done
        C_WTH=$(curl -s -m2 "${API_SERVER}/api/weather" 2>/dev/null || echo '[]')
        C_TM=$now
    fi
    
    # News: refresh every 600s (10 min)
    if [ $((now - C_RSS_TM)) -ge 600 ]; then
        C_RSS=$(curl -s -m5 "${API_SERVER}/api/rss/yahoo/market" 2>/dev/null || echo '{"items":[]}')
        C_RSS_TM=$now
    fi
    
    # System: always refresh
    C_SYS=$(curl -s -m1 "${API_SERVER}/api/system/status" 2>/dev/null || echo '{}')
}

draw() {
    local W=$(tput cols 2>/dev/null || echo 80)
    [ $W -lt 60 ] && W=60
    local now=$(date +%s)
    local sec=$((60 - (now - C_TM)))
    local news_sec=$((600 - (now - C_RSS_TM)))
    [ $sec -lt 0 ] && sec=0
    [ $news_sec -lt 0 ] && news_sec=0
    local news_min=$((news_sec / 60))
    local news_s=$((news_sec % 60))

    python3 << PYEOF
import json, re
from datetime import datetime

W = $W
sec = $sec
news_min = $news_min
news_s = $news_s
api = "$API_SERVER"

loc_raw = '''${C_LOC//\'/\\\'}'''
stk_raw = '''${C_STK//\'/\\\'}'''
wth_raw = '''${C_WTH//\'/\\\'}'''
sys_raw = '''${C_SYS//\'/\\\'}'''
rss_raw = '''${C_RSS//\'/\\\'}'''

RS, BD, DM = '\033[0m', '\033[1m', '\033[2m'
RD, GR, YL, BL, MG, CY, WH = '\033[91m', '\033[92m', '\033[93m', '\033[94m', '\033[95m', '\033[96m', '\033[37m'

def slen(s):
    return len(re.sub(r'\033\[[0-9;]*m', '', s))

def hline(c=DM):
    return f"{c}+{'-'*(W-2)}+{RS}"

def pline(t, c=DM):
    pad = W - 4 - slen(t)
    if pad < 0: 
        clean = re.sub(r'\033\[[0-9;]*m', '', t)
        t = clean[:W-7] + "..."
        pad = 0
    return f"{c}|{RS} {t}{' '*pad} {c}|{RS}"

def cline(t, c=DM):
    tlen = slen(t)
    lp = (W - 2 - tlen) // 2
    rp = W - 2 - tlen - lp
    return f"{c}|{RS}{' '*max(0,lp)}{t}{' '*max(0,rp)}{c}|{RS}"

L = []

# Header
L.append(hline(MG))
L.append(cline(f"{BD}MyAPI Dashboard{RS}", MG))
L.append(cline(f"{DM}{datetime.now().strftime('%Y-%m-%d %H:%M:%S')}{RS}", MG))
L.append(hline(MG))

# Location
loc = "N/A"
try:
    d = json.loads(loc_raw) if loc_raw else {}
    loc = d.get('weather', 'N/A') or 'N/A'
except: pass
L.append(cline(f"{WH}{loc}{RS}", DM))

# Stocks
L.append(hline(YL))
L.append(cline(f"{BD}{YL}[ STOCKS ]{RS} {DM}(refresh: {sec}s){RS}", YL))
L.append(hline(YL))

stocks = [
    ("SPY","S&P500"),("QLD","NAS2X"),("NVDA","NVIDIA"),("TSLA","TESLA"),
    ("SNPS","Synop"),("REKR","Rekor"),("SMCX","SMC"),("ETHU","ETH2X"),
    ("BITX","BTC2X"),("GLDM","Gold"),("XXRP","XRP"),("SOLT","SOL")
]
for sym, nm in stocks:
    p, chg, pct, clr = "---", "+0.00", "+0.00", WH
    try:
        for part in stk_raw.split(';'):
            if part.startswith(sym + '|'):
                raw = part.split('|', 1)[1]
                if raw and raw.strip().startswith('{'):
                    d = json.loads(raw)
                    cv = d.get('c', 0)
                    dv = d.get('d', 0)
                    dpv = d.get('dp', 0)
                    if cv:
                        p = f"{cv:.2f}"
                        chg = f"{dv:+.2f}" if dv else "+0.00"
                        pct = f"{dpv:+.2f}" if dpv else "+0.00"
                        clr = GR if dv and dv > 0 else RD if dv and dv < 0 else WH
                break
    except: pass
    L.append(pline(f"{CY}{sym:5}{RS} {nm:6} {clr}\${p:>8} {chg:>7} ({pct}%){RS}", YL))

# Weather
L.append(hline(BL))
L.append(cline(f"{BD}{BL}[ WEATHER ]{RS} {DM}(refresh: {sec}s){RS}", BL))
L.append(hline(BL))

def wicon(w):
    w = (w or '').lower()
    if 'clear' in w: return '*'
    if 'cloud' in w: return '#'
    if 'rain' in w: return '~'
    if 'snow' in w: return 'o'
    if any(x in w for x in ['mist','fog','haze']): return '='
    return '-'

try:
    data = json.loads(wth_raw) if wth_raw else []
    if data and isinstance(data, list):
        for i in range(0, len(data), 2):
            parts = []
            for j in range(2):
                if i + j < len(data):
                    c = data[i + j]
                    city = (c.get('city') or '?')[:6]
                    t = c.get('temperatureCelsius') or 0
                    h = c.get('humidity') or 0
                    w = c.get('weather') or ''
                    ic = wicon(w)
                    tc = CY if t <= 0 else BL if t <= 10 else GR if t <= 20 else YL
                    parts.append(f"[{ic}]{BD}{city:6}{RS} {tc}{t:5.1f}C{RS} {h:2}%")
            L.append(pline("  ".join(parts), BL))
    else:
        L.append(pline("No data", BL))
except:
    L.append(pline("Error", BL))

# News (Yahoo Finance) - 10 min refresh
L.append(hline(MG))
L.append(cline(f"{BD}{MG}[ YAHOO NEWS ]{RS} {DM}(refresh: {news_min}m{news_s}s){RS}", MG))
L.append(hline(MG))

try:
    rss = json.loads(rss_raw) if rss_raw else {}
    items = rss.get('items', [])[:5]
    if items:
        for item in items:
            title = (item.get('title') or '')
            if len(title) > W - 6:
                title = title[:W-9] + "..."
            L.append(pline(f"{WH}{title}{RS}", MG))
    else:
        L.append(pline("No news", MG))
except:
    L.append(pline("Error", MG))

# System Status
L.append(hline(GR))
L.append(cline(f"{BD}{GR}[ SYSTEM ]{RS} {DM}(real-time){RS}", GR))
L.append(hline(GR))

try:
    d = json.loads(sys_raw) if sys_raw else {}
    if d:
        def bar(p, w=12):
            f = int(max(0, min(100, p)) / 100 * w)
            c = GR if p < 60 else YL if p < 80 else RD
            return c + '#' * f + '-' * (w - f) + RS
        
        def fmt(b):
            for u in ['B','K','M','G']:
                if b < 1024: return f"{b:.0f}{u}"
                b /= 1024
            return f"{b:.0f}T"
        
        cpu = max(0, d.get('systemCpuLoad') or 0)
        proc = max(0, d.get('processCpuLoad') or 0)
        mm = d.get('memoryUsagePercent') or 0
        mu = d.get('usedPhysicalMemory') or 0
        mt = d.get('totalPhysicalMemory') or 1
        hp = d.get('heapUsagePercent') or 0
        hu = d.get('heapUsed') or 0
        hm = d.get('heapMax') or 1
        th = d.get('threadCount') or 0
        gc = d.get('gcCount') or 0
        gt = d.get('gcTime') or 0
        up = (d.get('uptimeMillis') or 0) // 1000
        
        L.append(pline(f"{BD}CPU {RS}{cpu:5.1f}% {bar(cpu)}  {BD}PROC{RS} {proc:5.1f}% {bar(proc)}", GR))
        L.append(pline(f"{BD}MEM {RS}{mm:5.1f}% {bar(mm)}  {fmt(mu)}/{fmt(mt)}", GR))
        L.append(pline(f"{BD}HEAP{RS} {hp:5.1f}% {bar(hp)}  {fmt(hu)}/{fmt(hm)}", GR))
        L.append(pline(f"{BD}THR {RS}{th:3}  {BD}GC{RS} {gc}/{gt}ms  {BD}UP{RS} {up//3600}h{(up%3600)//60}m{up%60}s", GR))
    else:
        L.append(pline("No data", GR))
except:
    L.append(pline("Error", GR))

# Footer
L.append(hline(DM))
L.append(cline(f"{DM}Server: {api} | Exit: Ctrl+C{RS}", DM))
L.append(hline(DM))

# Output
print('\033[H\033[J', end='')
print('\n'.join(L))
PYEOF
}

main() {
    printf '\033[2J\033[H'
    tput civis 2>/dev/null
    trap 'tput cnorm 2>/dev/null; printf "\033[2J\033[H"; echo "Bye!"; exit 0' INT TERM
    fetch
    while true; do
        draw
        sleep 1
        fetch
    done
}

[[ "$1" == "-h" || "$1" == "--help" ]] && { echo "Usage: $0 [-s SERVER]"; exit 0; }
while [[ $# -gt 0 ]]; do case $1 in -s|--server) API_SERVER="$2"; shift 2;; *) shift;; esac; done
main
