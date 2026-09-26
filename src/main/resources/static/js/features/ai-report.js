// ===========================================
// AI Report Feature Module
// ===========================================
//
// 2026-09-26 개편
//  - 생성 파라미터(Temperature·Top P·Top K·Penalty) 입력을 없앴다. 서버 고정값을 쓴다
//    (AIReportController.FIXED_SETTINGS). 판단 근거가 화면에 없는 입력이었고,
//    게이트웨이가 OpenRouter 로 번역해 넘기므로 일부는 전달되지도 않았다.
//  - 진행률이 2초짜리 타이머로 도는 가짜였다. 이제 서버가 SSE 로 보내는 실제 단계를 찍는다.
//  - 타입라이터 흉내를 걷어냈다. 모델이 뱉는 대로 본문이 흐른다.

import { showToast } from '../ui.js';
import {
    userId,
    stocksData,
    weatherData,
    yahooNewsData,
    yonhapNewsData,
    stockNewsData
} from '../state.js';

const CHECKBOX_STATE_KEY = 'aiReportCheckboxState';
const REPORT_STORAGE_KEY = 'aiReportLastReport';

/** 진행 중 상태. 중복 실행을 막고 경과 시간 타이머를 정리하는 데 쓴다. */
let running = false;
let elapsedTimer = null;

function loadCheckboxState() {
    try {
        const saved = localStorage.getItem(CHECKBOX_STATE_KEY);
        if (saved) return JSON.parse(saved);
    } catch (e) {
        console.error('Failed to load checkbox state:', e);
    }
    return {
        news: true, weather: true, traffic: true, emergency: true, stocks: true,
        yahooFinance: true, yonhapNews: true, lifeInfo: true, system: true
    };
}

function saveCheckboxState(state) {
    try {
        localStorage.setItem(CHECKBOX_STATE_KEY, JSON.stringify(state));
    } catch (e) {
        console.error('Failed to save checkbox state:', e);
    }
}

function saveLastReport(html) {
    try {
        localStorage.setItem(REPORT_STORAGE_KEY, html);
    } catch (e) {
        console.error('Failed to save last report:', e);
    }
}

function loadLastReport() {
    try {
        return localStorage.getItem(REPORT_STORAGE_KEY);
    } catch (e) {
        return null;
    }
}

/**
 * Open AI report modal
 */
export function openAIReportModal() {
    const modal = document.getElementById('ai-report-modal');
    if (!modal) return;
    modal.classList.add('active');

    const checkboxState = loadCheckboxState();
    const map = {
        'ai-report-news': 'news',
        'ai-report-weather': 'weather',
        'ai-report-traffic': 'traffic',
        'ai-report-emergency': 'emergency',
        'ai-report-stocks': 'stocks',
        'ai-report-yahoo-finance': 'yahooFinance',
        'ai-report-yonhap-news': 'yonhapNews',
        'ai-report-life-info': 'lifeInfo',
        'ai-report-system': 'system'
    };
    Object.entries(map).forEach(([id, key]) => {
        const el = document.getElementById(id);
        if (el) el.checked = checkboxState[key] !== false;
    });

    const lastReport = loadLastReport();
    const resultDiv = document.getElementById('ai-report-result');
    const loadingDiv = document.getElementById('ai-report-loading');
    const emptyDiv = document.getElementById('ai-report-empty');
    const contentDiv = document.getElementById('ai-report-content');

    loadingDiv.style.display = 'none';
    if (lastReport) {
        contentDiv.innerHTML = lastReport;
        resultDiv.style.display = 'block';
        emptyDiv.style.display = 'none';
    } else {
        contentDiv.textContent = '';
        resultDiv.style.display = 'none';
        emptyDiv.style.display = 'block';
    }
}

export function closeAIReportModal() {
    const modal = document.getElementById('ai-report-modal');
    if (modal) modal.classList.remove('active');
}

/**
 * Build session snapshot from current SSE/dashboard state
 */
function buildSessionSnapshot() {
    const snapshot = { fetchedAt: new Date().toISOString() };
    if (stocksData?.quotes?.length) snapshot.stocks = stocksData;
    if (weatherData?.length) snapshot.weather = weatherData;
    if (yahooNewsData?.length) snapshot.yahooNews = yahooNewsData;
    if (yonhapNewsData?.length) snapshot.yonhapNews = yonhapNewsData;
    if (stockNewsData?.length) snapshot.stockNews = stockNewsData;
    return snapshot;
}

// ── 진행 표시 ───────────────────────────────────────────────────────────
// 분모는 "선택한 데이터 수 + LLM 호출 + 이력 저장" 으로 잡는다. 실제로 서버가 밟는
// 단계 수와 같아서, 막대가 차는 속도가 진짜 진행과 어긋나지 않는다.

function setStage(label) {
    const el = document.getElementById('ai-report-progress-text');
    if (el) el.textContent = label;

    const logEl = document.getElementById('ai-report-stage-log');
    if (logEl) {
        const line = document.createElement('div');
        line.textContent = '· ' + label;
        logEl.appendChild(line);
        logEl.scrollTop = logEl.scrollHeight;
    }
}

function setProgress(done, total) {
    const fill = document.getElementById('ai-report-progress-fill');
    if (fill) {
        const pct = total > 0 ? Math.min(100, Math.round((done / total) * 100)) : 0;
        fill.style.width = pct + '%';
    }
}

function startElapsed() {
    const el = document.getElementById('ai-report-elapsed');
    const t0 = Date.now();
    stopElapsed();
    elapsedTimer = setInterval(() => {
        if (el) el.textContent = ((Date.now() - t0) / 1000).toFixed(0) + '초';
    }, 250);
}

function stopElapsed() {
    if (elapsedTimer) {
        clearInterval(elapsedTimer);
        elapsedTimer = null;
    }
}

/**
 * Generate AI report — 서버 진행 상황을 SSE 로 받아 그대로 보여준다.
 */
export async function generateAIReport() {
    if (running) {
        showToast('이미 리포트를 만들고 있어요', 'warning');
        return;
    }

    const topics = {
        news: document.getElementById('ai-report-news').checked,
        weather: document.getElementById('ai-report-weather').checked,
        traffic: document.getElementById('ai-report-traffic').checked,
        emergency: document.getElementById('ai-report-emergency').checked,
        stocks: document.getElementById('ai-report-stocks').checked,
        yahooFinance: document.getElementById('ai-report-yahoo-finance').checked,
        yonhapNews: document.getElementById('ai-report-yonhap-news').checked,
        lifeInfo: document.getElementById('ai-report-life-info').checked,
        system: document.getElementById('ai-report-system').checked
    };
    saveCheckboxState(topics);

    const selected = Object.values(topics).filter(Boolean).length;
    if (selected === 0) {
        showToast('최소 하나 이상의 데이터를 선택해주세요', 'warning');
        return;
    }

    const resultDiv = document.getElementById('ai-report-result');
    const loadingDiv = document.getElementById('ai-report-loading');
    const emptyDiv = document.getElementById('ai-report-empty');
    const contentDiv = document.getElementById('ai-report-content');
    const logEl = document.getElementById('ai-report-stage-log');

    running = true;
    if (logEl) logEl.innerHTML = '';
    emptyDiv.style.display = 'none';
    loadingDiv.style.display = 'block';
    resultDiv.style.display = 'block';
    contentDiv.textContent = '';
    setStage('요청 보내는 중');
    // 서버 단계 수 = 수집원(selected) + 요청접수 + AI 모델 호출 + 이력 저장
    const totalStages = selected + 3;
    setProgress(0, totalStages);
    startElapsed();

    let stagesDone = 0;
    let buffer = '';          // 스트림으로 받은 본문 누적
    let finalReport = null;

    try {
        const response = await fetch('api/ai-report/generate/stream', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Accept': 'text/event-stream',
                'X-User-Id': userId
            },
            body: JSON.stringify({ topics, snapshot: buildSessionSnapshot() })
        });

        if (!response.ok || !response.body) {
            throw new Error('HTTP ' + response.status);
        }

        const reader = response.body.getReader();
        const decoder = new TextDecoder();
        let raw = '';

        // SSE 프레임 파싱. EventSource 를 못 쓰는 이유는 POST 여야 하기 때문이다
        // (topics·snapshot 을 쿼리로 보내기엔 크다).
        for (;;) {
            const { done, value } = await reader.read();
            if (done) break;
            raw += decoder.decode(value, { stream: true });

            let sep;
            while ((sep = raw.indexOf('\n\n')) >= 0) {
                const frame = raw.slice(0, sep);
                raw = raw.slice(sep + 2);

                let eventName = 'message';
                const dataLines = [];
                frame.split('\n').forEach(line => {
                    if (line.startsWith('event:')) eventName = line.slice(6).trim();
                    else if (line.startsWith('data:')) dataLines.push(line.slice(5).replace(/^ /, ''));
                });
                if (dataLines.length === 0) continue;

                let payload;
                try {
                    payload = JSON.parse(dataLines.join('\n'));
                } catch {
                    continue;
                }

                if (eventName === 'stage') {
                    stagesDone++;
                    setStage(payload.label);
                    setProgress(stagesDone, totalStages);
                } else if (eventName === 'delta') {
                    buffer += payload.t || '';
                    contentDiv.innerHTML = convertMarkdownToHtml(buffer);
                    resultDiv.scrollTop = resultDiv.scrollHeight;
                } else if (eventName === 'done') {
                    finalReport = payload.report;
                } else if (eventName === 'error') {
                    throw new Error(payload.detail || payload.message);
                }
            }
        }

        const reportText = finalReport || buffer;
        if (!reportText) throw new Error('빈 응답');

        const html = convertMarkdownToHtml(reportText);
        contentDiv.innerHTML = html;
        saveLastReport(html);

        setProgress(1, 1);
        setStage('완료');
        showToast('리포트 생성이 완료되었어요', 'success');

    } catch (error) {
        console.error('Failed to generate AI report:', error);
        setStage('실패: ' + (error.message || '알 수 없는 오류'));
        // 🔴 여기까지 받은 본문은 지우지 않는다 — 중간에 끊겨도 읽을 수 있는 게 낫다.
        if (!buffer) {
            resultDiv.style.display = 'none';
            emptyDiv.style.display = 'block';
        }
        showToast('리포트 생성 중 오류가 발생했어요', 'danger');
    } finally {
        running = false;
        stopElapsed();
        setTimeout(() => {
            const el = document.getElementById('ai-report-loading');
            if (el) el.style.display = 'none';
        }, 1500);
    }
}

/**
 * Convert markdown to HTML (simple conversion)
 */
function convertMarkdownToHtml(markdown) {
    let html = markdown;
    html = html.replace(/^### (.*$)/gim, '<h3>$1</h3>');
    html = html.replace(/^## (.*$)/gim, '<h2>$1</h2>');
    html = html.replace(/^# (.*$)/gim, '<h1>$1</h1>');
    html = html.replace(/\*\*(.*?)\*\*/gim, '<strong>$1</strong>');
    html = html.replace(/^\* (.*$)/gim, '<li>$1</li>');
    html = html.replace(/^- (.*$)/gim, '<li>$1</li>');
    html = html.replace(/(<li>.*<\/li>)/s, '<ul>$1</ul>');
    html = html.replace(/\n/g, '<br>');
    return html;
}
