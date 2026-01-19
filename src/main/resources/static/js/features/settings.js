// ===========================================
// Settings Feature Module
// ===========================================

import { userId, uiState, saveUiState, config, setConfig, alertRulesData, setAlertRulesData, editingRuleId, setEditingRuleId } from '../state.js';
import { showToast, switchTab } from '../ui.js';
import { loadConfig, updateYouTubePlayer, connectSSE } from '../sse.js';
import { startStockHighlight } from './stocks.js';
import { 
    startSocialNewsAutoSlide, stopSocialNewsAutoSlide, renderSocialNews,
    startTrafficAutoSlide, stopTrafficAutoSlide, renderTraffic
} from './social.js';

/**
 * Populate profile information
 */
async function populateProfileInfo() {
    // User ID
    const userIdEl = document.getElementById('profile-user-id');
    if (userIdEl) {
        userIdEl.textContent = userId;
    }

    // Location info
    const locationEl = document.getElementById('profile-location');
    if (locationEl) {
        try {
            const response = await fetch('/api/location/weather');
            if (response.ok) {
                const data = await response.json();
                locationEl.textContent = data.location || '알 수 없음';
            } else {
                locationEl.textContent = '알 수 없음';
            }
        } catch (error) {
            locationEl.textContent = '알 수 없음';
        }
    }

    // Browser info
    const browserEl = document.getElementById('profile-browser');
    if (browserEl) {
        const ua = navigator.userAgent;
        let browser = '알 수 없음';
        if (ua.includes('Chrome')) browser = 'Chrome';
        else if (ua.includes('Firefox')) browser = 'Firefox';
        else if (ua.includes('Safari')) browser = 'Safari';
        else if (ua.includes('Edge')) browser = 'Edge';
        browserEl.textContent = browser;
    }

    // Screen resolution
    const resolutionEl = document.getElementById('profile-resolution');
    if (resolutionEl) {
        resolutionEl.textContent = `${window.screen.width} x ${window.screen.height}`;
    }

    // Language
    const languageEl = document.getElementById('profile-language');
    if (languageEl) {
        languageEl.textContent = navigator.language || navigator.userLanguage || '알 수 없음';
    }

    // Timezone
    const timezoneEl = document.getElementById('profile-timezone');
    if (timezoneEl) {
        timezoneEl.textContent = Intl.DateTimeFormat().resolvedOptions().timeZone || '알 수 없음';
    }
}

/**
 * Open settings modal
 */
export function openSettings() {
    const modal = document.getElementById('settings-modal');
    if (modal) {
        modal.classList.add('active');
        populateSettingsForm();
        loadAlertRules();
    }
}

/**
 * Close settings modal
 */
export function closeSettings() {
    const modal = document.getElementById('settings-modal');
    if (modal) {
        modal.classList.remove('active');
    }
}

/**
 * Populate settings form with current values
 */
function populateSettingsForm() {
    // Media tab
    const youtubeInput = document.getElementById('settings-youtube');
    if (youtubeInput && config) {
        youtubeInput.value = config.youtubeUrl || '';
    }

    // Stocks tab
    renderTickerList();

    // Stock highlight settings
    const highlightIntervalInput = document.getElementById('settings-highlight-interval');
    const autoHighlightCheckbox = document.getElementById('settings-auto-highlight');
    if (highlightIntervalInput) {
        highlightIntervalInput.value = uiState.stocks.highlightInterval || 10;
    }
    if (autoHighlightCheckbox) {
        autoHighlightCheckbox.checked = uiState.stocks.autoHighlight !== false;
    }

    // News tab
    const autoSlideCheckbox = document.getElementById('settings-auto-slide');
    const slideIntervalInput = document.getElementById('settings-slide-interval');
    if (autoSlideCheckbox) {
        autoSlideCheckbox.checked = uiState.news.autoSlide;
    }
    if (slideIntervalInput) {
        slideIntervalInput.value = uiState.news.slideInterval || 5;
    }

    // Social news tab
    const socialNewsAutoSlideCheckbox = document.getElementById('settings-social-news-auto-slide');
    const socialNewsSlideIntervalInput = document.getElementById('settings-social-news-slide-interval');
    if (socialNewsAutoSlideCheckbox) {
        socialNewsAutoSlideCheckbox.checked = uiState.socialNews.autoSlide;
    }
    if (socialNewsSlideIntervalInput) {
        socialNewsSlideIntervalInput.value = uiState.socialNews.slideInterval || 5;
    }

    // Traffic tab
    const trafficAutoSlideCheckbox = document.getElementById('settings-traffic-auto-slide');
    const trafficSlideIntervalInput = document.getElementById('settings-traffic-slide-interval');
    if (trafficAutoSlideCheckbox) {
        trafficAutoSlideCheckbox.checked = uiState.traffic.autoSlide;
    }
    if (trafficSlideIntervalInput) {
        trafficSlideIntervalInput.value = uiState.traffic.slideInterval || 5;
    }

    // System tab
    const cpuWarningInput = document.getElementById('settings-cpu-warning');
    const cpuDangerInput = document.getElementById('settings-cpu-danger');
    const memWarningInput = document.getElementById('settings-mem-warning');
    const memDangerInput = document.getElementById('settings-mem-danger');
    
    if (cpuWarningInput) cpuWarningInput.value = uiState.system.cpuWarning;
    if (cpuDangerInput) cpuDangerInput.value = uiState.system.cpuDanger;
    if (memWarningInput) memWarningInput.value = uiState.system.memWarning;
    if (memDangerInput) memDangerInput.value = uiState.system.memDanger;

    // Profile tab
    populateProfileInfo();
}

/**
 * Render ticker list in settings
 */
export function renderTickerList() {
    const container = document.getElementById('ticker-list');
    if (!container || !config || !config.tickers) return;

    const html = config.tickers.map((ticker, index) => `
        <div class="ticker-item">
            <input type="text" class="form-input" value="${ticker.symbol}" 
                onchange="updateTicker(${index}, 'symbol', this.value)" placeholder="심볼">
            <input type="text" class="form-input" value="${ticker.name}" 
                onchange="updateTicker(${index}, 'name', this.value)" placeholder="이름">
            <button class="ticker-remove" onclick="removeTicker(${index})">✕</button>
        </div>
    `).join('');

    container.innerHTML = html;
}

/**
 * Add new ticker
 */
export function addTicker() {
    if (!config) return;
    if (!config.tickers) config.tickers = [];
    config.tickers.push({ symbol: '', name: '' });
    console.log('[Settings] Ticker added, total:', config.tickers.length);
    renderTickerList();
}

/**
 * Update ticker
 */
export function updateTicker(index, field, value) {
    if (!config || !config.tickers) return;
    config.tickers[index][field] = value;
    console.log('[Settings] Ticker updated:', config.tickers[index]);
}

/**
 * Remove ticker
 */
export function removeTicker(index) {
    if (!config || !config.tickers) return;
    config.tickers.splice(index, 1);
    renderTickerList();
}

/**
 * Save settings
 */
export async function saveSettings() {
    // Collect form data
    const youtubeUrl = document.getElementById('settings-youtube')?.value || '';
    const highlightInterval = parseInt(document.getElementById('settings-highlight-interval')?.value) || 10;
    const autoHighlight = document.getElementById('settings-auto-highlight')?.checked !== false;
    const autoSlide = document.getElementById('settings-auto-slide')?.checked || false;
    const slideInterval = parseInt(document.getElementById('settings-slide-interval')?.value) || 5;
    const socialNewsAutoSlide = document.getElementById('settings-social-news-auto-slide')?.checked || false;
    const socialNewsSlideInterval = parseInt(document.getElementById('settings-social-news-slide-interval')?.value) || 5;
    const trafficAutoSlide = document.getElementById('settings-traffic-auto-slide')?.checked || false;
    const trafficSlideInterval = parseInt(document.getElementById('settings-traffic-slide-interval')?.value) || 5;
    const cpuWarning = parseInt(document.getElementById('settings-cpu-warning')?.value) || 70;
    const cpuDanger = parseInt(document.getElementById('settings-cpu-danger')?.value) || 90;
    const memWarning = parseInt(document.getElementById('settings-mem-warning')?.value) || 70;
    const memDanger = parseInt(document.getElementById('settings-mem-danger')?.value) || 90;

    // Update uiState
    uiState.stocks.highlightInterval = highlightInterval;
    uiState.stocks.autoHighlight = autoHighlight;
    uiState.news.autoSlide = autoSlide;
    uiState.news.slideInterval = slideInterval;
    uiState.socialNews.autoSlide = socialNewsAutoSlide;
    uiState.socialNews.slideInterval = socialNewsSlideInterval;
    uiState.traffic.autoSlide = trafficAutoSlide;
    uiState.traffic.slideInterval = trafficSlideInterval;
    uiState.system.cpuWarning = cpuWarning;
    uiState.system.cpuDanger = cpuDanger;
    uiState.system.memWarning = memWarning;
    uiState.system.memDanger = memDanger;
    saveUiState();

    // Save to server
    try {
        // 현재 설정된 티커 목록 가져오기 (빈 심볼 제외)
        const validTickers = (config?.tickers || []).filter(t => t.symbol && t.symbol.trim() !== '');
        
        console.log('[Settings] Saving config with tickers:', validTickers);
        
        const response = await fetch('/api/dashboard/config', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-User-Id': userId
            },
            body: JSON.stringify({
                youtubeUrl,
                tickers: validTickers
            })
        });

        if (response.ok) {
            const savedConfig = await response.json();
            console.log('[Settings] Config saved, server returned:', savedConfig);
            showToast('설정이 저장되었습니다', 'info');
            
            // 서버에서 반환된 설정으로 config 업데이트
            setConfig(savedConfig);
            
            // Only update YouTube player if URL actually changed
            if (savedConfig.youtubeUrl && savedConfig.youtubeUrl !== youtubeUrl) {
                updateYouTubePlayer(savedConfig.youtubeUrl);
            }
            
            // 기존 SSE 연결 종료 후 재연결하여 새 설정으로 데이터 받기
            // 약간의 지연을 두어 서버의 broadcastFullDataForUser가 완료되도록 함
            setTimeout(() => {
                console.log('[Settings] Reconnecting SSE with new config');
                connectSSE();
            }, 100);
            
            startStockHighlight();
            
            // Update social news and traffic auto slide
            if (uiState.socialNews.autoSlide) {
                startSocialNewsAutoSlide();
            } else {
                stopSocialNewsAutoSlide();
            }
            renderSocialNews();
            
            if (uiState.traffic.autoSlide) {
                startTrafficAutoSlide();
            } else {
                stopTrafficAutoSlide();
            }
            renderTraffic();
            
            closeSettings();
        } else {
            throw new Error('Failed to save');
        }
    } catch (error) {
        console.error('Failed to save settings:', error);
        showToast('설정 저장 실패', 'danger');
    }
}

/**
 * Load alert rules
 */
export async function loadAlertRules() {
    try {
        const response = await fetch('/api/alerts/rules', {
            headers: { 'X-User-Id': userId }
        });
        if (response.ok) {
            setAlertRulesData(await response.json());
            renderAlertRules();
        }
    } catch (error) {
        console.error('Failed to load alert rules:', error);
    }
}

/**
 * Render alert rules
 */
export function renderAlertRules() {
    const container = document.getElementById('alert-rules-list');
    if (!container) return;

    if (!alertRulesData || alertRulesData.length === 0) {
        container.innerHTML = '<div class="no-data">알림 규칙이 없습니다</div>';
        return;
    }

    const html = alertRulesData.map(rule => `
        <div class="alert-rule-item">
            <div class="alert-rule-status ${rule.enabled ? '' : 'disabled'}"></div>
            <div class="alert-rule-info">
                <span class="alert-rule-type">${rule.type}</span>
                <span class="alert-rule-desc">${rule.description || formatRuleDesc(rule)}</span>
            </div>
            <button class="alert-rule-toggle ${rule.enabled ? 'enabled' : ''}" 
                onclick="toggleAlertRule(${rule.id})">${rule.enabled ? '✓' : '○'}</button>
            <button class="alert-rule-delete" onclick="deleteAlertRule(${rule.id})">✕</button>
        </div>
    `).join('');

    container.innerHTML = html;
}

/**
 * Format rule description
 */
function formatRuleDesc(rule) {
    return `${rule.condition} ${rule.operator} ${rule.threshold}`;
}

/**
 * Toggle alert rule
 */
export async function toggleAlertRule(id) {
    const rule = alertRulesData.find(r => r.id === id);
    if (!rule) return;

    try {
        await fetch(`/api/alerts/rules/${id}`, {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json',
                'X-User-Id': userId
            },
            body: JSON.stringify({ ...rule, enabled: !rule.enabled })
        });
        loadAlertRules();
    } catch (error) {
        console.error('Failed to toggle alert rule:', error);
    }
}

/**
 * Delete alert rule
 */
export async function deleteAlertRule(id) {
    try {
        await fetch(`/api/alerts/rules/${id}`, {
            method: 'DELETE',
            headers: { 'X-User-Id': userId }
        });
        loadAlertRules();
    } catch (error) {
        console.error('Failed to delete alert rule:', error);
    }
}

/**
 * Show add rule form
 */
export function showAddRuleForm() {
    setEditingRuleId(null);
    const form = document.getElementById('alert-rule-form');
    if (form) {
        form.style.display = 'grid';
        document.getElementById('rule-type').value = 'STOCK_PRICE';
        document.getElementById('rule-condition').value = '';
        document.getElementById('rule-operator').value = 'GREATER_THAN';
        document.getElementById('rule-threshold').value = '';
    }
}

/**
 * Cancel rule form
 */
export function cancelRuleForm() {
    const form = document.getElementById('alert-rule-form');
    if (form) {
        form.style.display = 'none';
    }
}

/**
 * Save alert rule
 */
export async function saveAlertRule() {
    const type = document.getElementById('rule-type')?.value;
    const condition = document.getElementById('rule-condition')?.value;
    const operator = document.getElementById('rule-operator')?.value;
    const threshold = parseFloat(document.getElementById('rule-threshold')?.value);

    if (!type || !condition || !operator || isNaN(threshold)) {
        showToast('모든 필드를 입력하세요', 'warning');
        return;
    }

    try {
        const method = editingRuleId ? 'PUT' : 'POST';
        const url = editingRuleId 
            ? `/api/alerts/rules/${editingRuleId}` 
            : '/api/alerts/rules';

        await fetch(url, {
            method,
            headers: {
                'Content-Type': 'application/json',
                'X-User-Id': userId
            },
            body: JSON.stringify({
                type,
                condition,
                operator,
                threshold,
                enabled: true
            })
        });

        cancelRuleForm();
        loadAlertRules();
        showToast('알림 규칙이 저장되었습니다', 'info');
    } catch (error) {
        console.error('Failed to save alert rule:', error);
        showToast('알림 규칙 저장 실패', 'danger');
    }
}
