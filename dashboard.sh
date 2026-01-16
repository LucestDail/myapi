#!/bin/bash
#############################################
# MyAPI Dashboard
#############################################

API_SERVER="${API_SERVER:-http://localhost:8080}"

# Cache
C_LOC="" C_STK="" C_WTH="" C_SYS="" C_RSS="" C_TM=0

refresh_check() { [ $(($(date +%s) - C_TM)) -ge 60 ]; }

fetch() {
    if refresh_check; then
        C_LOC=$(curl -s -m2 "${API_SERVER}/api/location/weather" 2>/dev/null)
        C_STK=""
        for s in SPY QLD NVDA TSLA SNPS REKR SMCX ETHU BITX GLDM XXRP SOLT; do
            C_STK+="${s}|$(curl -s -m2 "${API_SERVER}/api/finnhub/quote?symbol=${s}" 2>/dev/null);"
        done
        C_WTH=$(curl -s -m2 "${API_SERVER}/api/weather" 2>/dev/null)
        C_RSS=$(curl -s -m3 "${API_SERVER}/api/rss/reuters/top" 2>/dev/null)
        C_TM=$(date +%s)
    fi
    C_SYS=$(curl -s -m1 "${API_SERVER}/api/system/status" 2>/dev/null)
}

draw() {
    local W=$(tput cols 2>/dev/null || echo 80)
    [ $W -lt 60 ] && W=60
    local sec=$((60 - ($(date +%s) - C_TM)))
    [ $sec -lt 0 ] && sec=0

    python3 - "$W" "$sec" "$API_SERVER" "$C_LOC" "$C_STK" "$C_WTH" "$C_SYS" "$C_RSS" << 'PYEND'
import json, sys, re
from datetime import datetime

W = int(sys.argv[1])
sec = int(sys.argv[2])
api = sys.argv[3]
loc_raw = sys.argv[4]
stk_raw = sys.argv[5]
wth_raw = sys.argv[6]
sys_raw = sys.argv[7]
rss_raw = sys.argv[8] if len(sys.argv) > 8 else ""

RS, BD, DM = '\033[0m', '\033[1m', '\033[2m'
RD, GR, YL, BL, MG, CY, WH = '\033[91m', '\033[92m', '\033[93m', '\033[94m', '\033[95m', '\033[96m', '\033[37m'

def hline(c=DM): return f"{c}+{'-'*(W-2)}+{RS}"
def pline(t, c=DM):
    clean = re.sub(r'\033\[[0-9;]*m', '', t)
    pad = W - 4 - len(clean)
    return f"{c}|{RS} {t}{' '*max(0,pad)} {c}|{RS}"
def cline(t, c=DM):
    clean = re.sub(r'\033\[[0-9;]*m', '', t)
    lp = (W - 2 - len(clean)) // 2
    rp = W - 2 - len(clean) - lp
    return f"{c}|{RS}{' '*max(0,lp)}{t}{' '*max(0,rp)}{c}|{RS}"

L = []

L.append(hline(MG))
L.append(cline(f"{BD}MyAPI Dashboard{RS}", MG))
L.append(cline(f"{DM}{datetime.now().strftime('%Y-%m-%d %H:%M:%S')} | Refresh: {sec}s{RS}", MG))
L.append(hline(MG))

loc = "Loading..."
try: loc = json.loads(loc_raw).get('weather', 'N/A')
except: pass
L.append(cline(f"{WH}{loc}{RS}", DM))

L.append(hline(YL))
L.append(cline(f"{BD}{YL}[ US STOCKS ]{RS}", YL))
L.append(hline(YL))

stocks = [("SPY","S&P500"),("QLD","NAS2X"),("NVDA","NVIDIA"),("TSLA","TESLA"),
          ("SNPS","Synop"),("REKR","Rekor"),("SMCX","SMC"),("ETHU","ETH2X"),
          ("BITX","BTC2X"),("GLDM","Gold"),("XXRP","XRP"),("SOLT","SOL")]
for sym, nm in stocks:
    p, chg, pct, clr = "---", "+0.00", "+0.00", WH
    try:
        for part in stk_raw.split(';'):
            if part.startswith(sym + '|'):
                raw = part.split('|', 1)[1]
                if raw and '{' in raw:
                    d = json.loads(raw)
                    p = f"{d.get('c', 0):.2f}"
                    chg = f"{d.get('d', 0):+.2f}"
                    pct = f"{d.get('dp', 0):+.2f}"
                    clr = GR if d.get('d', 0) > 0 else RD if d.get('d', 0) < 0 else WH
                break
    except: pass
    L.append(pline(f"{CY}{sym:5}{RS} {nm:6} {clr}${p:>8} {chg:>7} ({pct}%){RS}", YL))

L.append(hline(BL))
L.append(cline(f"{BD}{BL}[ KOREA WEATHER ]{RS}", BL))
L.append(hline(BL))

def wicon(w):
    w = (w or '').lower()
    if 'clear' in w: return '*'
    if 'cloud' in w: return '#'
    if 'rain' in w: return '~'
    if 'snow' in w: return 'o'
    if 'mist' in w or 'fog' in w or 'haze' in w: return '='
    return '-'

try:
    data = json.loads(wth_raw)
    for i in range(0, len(data), 2):
        line = ""
        for j in range(2):
            if i + j < len(data):
                c = data[i + j]
                city = c.get('city', '?')[:6]
                t = c.get('temperatureCelsius', 0)
                h = c.get('humidity', 0)
                w = c.get('weather', '')
                ic = wicon(w)
                tc = CY if t <= 0 else BL if t <= 10 else GR if t <= 20 else YL
                line += f"[{ic}]{BD}{city:6}{RS} {tc}{t:4.1f}C{RS} {h:2}%  "
        L.append(pline(line.strip(), BL))
except:
    L.append(pline("Loading...", BL))

L.append(hline(MG))
L.append(cline(f"{BD}{MG}[ REUTERS NEWS ]{RS}", MG))
L.append(hline(MG))

try:
    rss = json.loads(rss_raw) if rss_raw else {}
    items = rss.get('items', [])[:5]
    for item in items:
        title = item.get('title', '')[:W-8]
        L.append(pline(f"{WH}{title}{RS}", MG))
    if not items:
        L.append(pline("No news available", MG))
except:
    L.append(pline("Loading...", MG))

L.append(hline(GR))
L.append(cline(f"{BD}{GR}[ SYSTEM ]{RS}", GR))
L.append(hline(GR))

try:
    d = json.loads(sys_raw)
    def bar(p, w=10):
        f = int(p / 100 * w)
        c = GR if p < 60 else YL if p < 80 else RD
        return c + '#' * f + '-' * (w - f) + RS
    cpu = max(0, d.get('systemCpuLoad', 0))
    mm = d.get('memoryUsagePercent', 0)
    hp = d.get('heapUsagePercent', 0)
    up = d.get('uptimeMillis', 0) // 1000
    L.append(pline(f"{BD}CPU{RS} {cpu:4.1f}%{bar(cpu)} {BD}MEM{RS} {mm:4.1f}%{bar(mm)} {BD}HEAP{RS}{hp:4.1f}%{bar(hp)} {BD}UP{RS} {up//3600}h{(up%3600)//60}m", GR))
except:
    L.append(pline("Loading...", GR))

L.append(hline(DM))
L.append(cline(f"{DM}Server: {api} | Exit: Ctrl+C{RS}", DM))
L.append(hline(DM))

print('\033[H', end='')
for l in L: print(l)
print('\033[J', end='')
PYEND
}

main() {
    printf '\033[2J\033[H'
    tput civis 2>/dev/null
    trap 'tput cnorm 2>/dev/null; printf "\033[2J\033[H"; echo "Bye!"; exit 0' INT TERM
    fetch
    while true; do fetch; draw; sleep 1; done
}

[[ "$1" == "-h" || "$1" == "--help" ]] && { echo "Usage: $0 [-s SERVER]"; exit 0; }
while [[ $# -gt 0 ]]; do case $1 in -s|--server) API_SERVER="$2"; shift 2;; *) shift;; esac; done
main
