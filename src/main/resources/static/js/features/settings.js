// ===========================================
// Settings Feature Module
// ===========================================

import { userId, uiState, saveUiState, config, alertRulesData, setAlertRulesData, editingRuleId, setEditingRuleId } from '../state.js';
import { showToast, switchTab } from '../ui.js';
import { loadConfig, updateYouTubePlayer } from '../sse.js';
import { startStockHighlight } from './stocks.js';

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

    // System tab
    const cpuWarningInput = document.getElementById('settings-cpu-warning');
    const cpuDangerInput = document.getElementById('settings-cpu-danger');
    const memWarningInput = document.getElementById('settings-mem-warning');
    const memDangerInput = document.getElementById('settings-mem-danger');
    
    if (cpuWarningInput) cpuWarningInput.value = uiState.system.cpuWarning;
    if (cpuDangerInput) cpuDangerInput.value = uiState.system.cpuDanger;
    if (memWarningInput) memWarningInput.value = uiState.system.memWarning;
    if (memDangerInput) memDangerInput.value = uiState.system.memDanger;
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
    renderTickerList();
}

/**
 * Update ticker
 */
export function updateTicker(index, field, value) {
    if (!config || !config.tickers) return;
    config.tickers[index][field] = value;
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
    const cpuWarning = parseInt(document.getElementById('settings-cpu-warning')?.value) || 70;
    const cpuDanger = parseInt(document.getElementById('settings-cpu-danger')?.value) || 90;
    const memWarning = parseInt(document.getElementById('settings-mem-warning')?.value) || 70;
    const memDanger = parseInt(document.getElementById('settings-mem-danger')?.value) || 90;

    // Update uiState
    uiState.stocks.highlightInterval = highlightInterval;
    uiState.stocks.autoHighlight = autoHighlight;
    uiState.news.autoSlide = autoSlide;
    uiState.news.slideInterval = slideInterval;
    uiState.system.cpuWarning = cpuWarning;
    uiState.system.cpuDanger = cpuDanger;
    uiState.system.memWarning = memWarning;
    uiState.system.memDanger = memDanger;
    saveUiState();

    // Save to server
    try {
        const response = await fetch('/api/dashboard/config', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-User-Id': userId
            },
            body: JSON.stringify({
                youtubeUrl,
                tickers: config?.tickers || []
            })
        });

        if (response.ok) {
            showToast('설정이 저장되었습니다', 'info');
            // Only update YouTube player if URL actually changed
            if (config && config.youtubeUrl !== youtubeUrl) {
                updateYouTubePlayer(youtubeUrl);
            }
            // Update config
            if (config) {
                config.youtubeUrl = youtubeUrl;
            }
            startStockHighlight();
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
