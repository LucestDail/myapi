// ===========================================
// AI Report Feature Module
// ===========================================

import { showToast } from '../ui.js';

// 체크박스 설정 키
const CHECKBOX_STATE_KEY = 'aiReportCheckboxState';
const SETTINGS_STATE_KEY = 'aiReportSettingsState';
const REPORT_STORAGE_KEY = 'aiReportLastReport';

// 프로그레스 상태 메시지 (토스 라이팅)
const PROGRESS_MESSAGES = [
    { text: '데이터를 수집하고 있어요...', progress: 20 },
    { text: '정보를 분석하고 있어요...', progress: 40 },
    { text: '리포트를 구성하고 있어요...', progress: 60 },
    { text: '내용을 다듬고 있어요...', progress: 80 },
    { text: '거의 다 됐어요...', progress: 95 }
];

/**
 * Load checkbox state from localStorage
 */
function loadCheckboxState() {
    try {
        const saved = localStorage.getItem(CHECKBOX_STATE_KEY);
        if (saved) {
            return JSON.parse(saved);
        }
    } catch (e) {
        console.error('Failed to load checkbox state:', e);
    }
    // 기본값 - 모두 true
    return {
        news: true,
        weather: true,
        traffic: true,
        emergency: true,
        stocks: true,
        yahooFinance: true,
        yonhapNews: true,
        lifeInfo: true,
        system: true
    };
}

/**
 * Save checkbox state to localStorage
 */
function saveCheckboxState(state) {
    try {
        localStorage.setItem(CHECKBOX_STATE_KEY, JSON.stringify(state));
    } catch (e) {
        console.error('Failed to save checkbox state:', e);
    }
}

/**
 * Load settings state from localStorage
 */
function loadSettingsState() {
    try {
        const saved = localStorage.getItem(SETTINGS_STATE_KEY);
        if (saved) {
            return JSON.parse(saved);
        }
    } catch (e) {
        console.error('Failed to load settings state:', e);
    }
    // 기본값
    return {
        temperature: 1.0,
        topP: 0.95,
        topK: 40,
        presencePenalty: 0.0,
        frequencyPenalty: 0.0
    };
}

/**
 * Save settings state to localStorage
 */
function saveSettingsState(state) {
    try {
        localStorage.setItem(SETTINGS_STATE_KEY, JSON.stringify(state));
    } catch (e) {
        console.error('Failed to save settings state:', e);
    }
}

/**
 * Save last report to localStorage
 */
function saveLastReport(report) {
    try {
        localStorage.setItem(REPORT_STORAGE_KEY, report);
    } catch (e) {
        console.error('Failed to save last report:', e);
    }
}

/**
 * Load last report from localStorage
 */
function loadLastReport() {
    try {
        return localStorage.getItem(REPORT_STORAGE_KEY);
    } catch (e) {
        console.error('Failed to load last report:', e);
        return null;
    }
}

/**
 * Update progress display
 */
function updateProgress(messageIndex) {
    const progressText = document.getElementById('ai-report-progress-text');
    const progressFill = document.getElementById('ai-report-progress-fill');
    
    if (progressText && progressFill && messageIndex < PROGRESS_MESSAGES.length) {
        const msg = PROGRESS_MESSAGES[messageIndex];
        progressText.textContent = msg.text;
        progressFill.style.width = msg.progress + '%';
    }
}

/**
 * Typewriter effect for streaming display
 */
function typeWriter(element, text, callback) {
    const htmlText = convertMarkdownToHtml(text);
    let index = 0;
    const charsPerChunk = 3; // 한 번에 출력할 문자 수
    
    function type() {
        if (index < htmlText.length) {
            const chunk = htmlText.substring(0, index + charsPerChunk);
            element.innerHTML = chunk;
            index += charsPerChunk;
            setTimeout(type, 10); // 10ms마다 업데이트
        } else {
            if (callback) callback();
        }
    }
    
    type();
}

/**
 * Open AI report modal
 */
export function openAIReportModal() {
    const modal = document.getElementById('ai-report-modal');
    if (modal) {
        modal.classList.add('active');
        
        // 저장된 상태 로드
        const checkboxState = loadCheckboxState();
        const settingsState = loadSettingsState();
        
        // 체크박스에 상태 적용
        document.getElementById('ai-report-news').checked = checkboxState.news !== false;
        document.getElementById('ai-report-weather').checked = checkboxState.weather !== false;
        document.getElementById('ai-report-traffic').checked = checkboxState.traffic !== false;
        document.getElementById('ai-report-emergency').checked = checkboxState.emergency !== false;
        document.getElementById('ai-report-stocks').checked = checkboxState.stocks !== false;
        document.getElementById('ai-report-yahoo-finance').checked = checkboxState.yahooFinance !== false;
        document.getElementById('ai-report-yonhap-news').checked = checkboxState.yonhapNews !== false;
        document.getElementById('ai-report-life-info').checked = checkboxState.lifeInfo !== false;
        document.getElementById('ai-report-system').checked = checkboxState.system !== false;
        
        // 설정값 적용
        document.getElementById('ai-report-temperature').value = settingsState.temperature || 1.0;
        document.getElementById('ai-report-topP').value = settingsState.topP || 0.95;
        document.getElementById('ai-report-topK').value = settingsState.topK || 40;
        document.getElementById('ai-report-presencePenalty').value = settingsState.presencePenalty || 0.0;
        document.getElementById('ai-report-frequencyPenalty').value = settingsState.frequencyPenalty || 0.0;
        
        // 저장된 리포트가 있으면 표시
        const lastReport = loadLastReport();
        if (lastReport) {
            document.getElementById('ai-report-content').innerHTML = lastReport;
            document.getElementById('ai-report-result').style.display = 'block';
            document.getElementById('ai-report-loading').style.display = 'none';
            document.getElementById('ai-report-empty').style.display = 'none';
        } else {
            // 초기 상태 설정
            document.getElementById('ai-report-result').style.display = 'none';
            document.getElementById('ai-report-loading').style.display = 'none';
            document.getElementById('ai-report-empty').style.display = 'block';
            document.getElementById('ai-report-content').textContent = '';
        }
    }
}

/**
 * Close AI report modal
 */
export function closeAIReportModal() {
    const modal = document.getElementById('ai-report-modal');
    if (modal) {
        modal.classList.remove('active');
    }
}

/**
 * Generate AI report
 */
export async function generateAIReport() {
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

    // 설정값 수집
    const settings = {
        temperature: parseFloat(document.getElementById('ai-report-temperature').value) || 1.0,
        topP: parseFloat(document.getElementById('ai-report-topP').value) || 0.95,
        topK: parseInt(document.getElementById('ai-report-topK').value) || 40,
        presencePenalty: parseFloat(document.getElementById('ai-report-presencePenalty').value) || 0.0,
        frequencyPenalty: parseFloat(document.getElementById('ai-report-frequencyPenalty').value) || 0.0
    };

    // 상태 저장
    saveCheckboxState(topics);
    saveSettingsState(settings);

    // 최소 하나는 선택되어야 함
    if (!Object.values(topics).some(v => v)) {
        showToast('최소 하나 이상의 데이터를 선택해주세요', 'warning');
        return;
    }

    const resultDiv = document.getElementById('ai-report-result');
    const loadingDiv = document.getElementById('ai-report-loading');
    const emptyDiv = document.getElementById('ai-report-empty');
    const contentDiv = document.getElementById('ai-report-content');

    resultDiv.style.display = 'none';
    emptyDiv.style.display = 'none';
    loadingDiv.style.display = 'block';
    
    // 프로그레스 초기화
    updateProgress(0);
    let progressIndex = 0;
    const progressInterval = setInterval(() => {
        progressIndex++;
        if (progressIndex < PROGRESS_MESSAGES.length) {
            updateProgress(progressIndex);
        }
    }, 2000); // 2초마다 다음 메시지

    try {
        const response = await fetch('/api/ai-report/generate', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ 
                topics,
                settings 
            })
        });

        const data = await response.json();
        
        // 프로그레스 인터벌 정리
        clearInterval(progressInterval);
        
        if (response.ok && data.data && data.data.report) {
            const reportText = data.data.report;
            const reportHtml = convertMarkdownToHtml(reportText);
            
            // 리포트 저장
            saveLastReport(reportHtml);
            
            // 스트리밍 효과로 출력
            contentDiv.innerHTML = '';
            loadingDiv.style.display = 'block';
            resultDiv.style.display = 'block';
            
            // 프로그레스 완료 표시
            updateProgress(PROGRESS_MESSAGES.length - 1);
            const progressText = document.getElementById('ai-report-progress-text');
            if (progressText) {
                progressText.textContent = '리포트를 출력하고 있어요...';
            }
            const progressFill = document.getElementById('ai-report-progress-fill');
            if (progressFill) {
                progressFill.style.width = '100%';
            }
            
            // 타입라이터 효과로 출력
            typeWriter(contentDiv, reportText, () => {
                loadingDiv.style.display = 'none';
                showToast('리포트 생성이 완료되었어요', 'success');
            });
        } else {
            throw new Error(data.data?.error || '리포트 생성 실패');
        }
    } catch (error) {
        console.error('Failed to generate AI report:', error);
        clearInterval(progressInterval);
        loadingDiv.style.display = 'none';
        emptyDiv.style.display = 'block';
        contentDiv.textContent = '리포트 생성 중 오류가 발생했어요. 잠시 후 다시 시도해주세요.';
        resultDiv.style.display = 'none';
        showToast('리포트 생성 중 오류가 발생했어요', 'danger');
    }
}

/**
 * Convert markdown to HTML (simple conversion)
 */
function convertMarkdownToHtml(markdown) {
    let html = markdown;
    
    // Headers
    html = html.replace(/^### (.*$)/gim, '<h3>$1</h3>');
    html = html.replace(/^## (.*$)/gim, '<h2>$1</h2>');
    html = html.replace(/^# (.*$)/gim, '<h1>$1</h1>');
    
    // Bold
    html = html.replace(/\*\*(.*?)\*\*/gim, '<strong>$1</strong>');
    
    // Lists
    html = html.replace(/^\* (.*$)/gim, '<li>$1</li>');
    html = html.replace(/^- (.*$)/gim, '<li>$1</li>');
    html = html.replace(/(<li>.*<\/li>)/s, '<ul>$1</ul>');
    
    // Line breaks
    html = html.replace(/\n/g, '<br>');
    
    return html;
}
