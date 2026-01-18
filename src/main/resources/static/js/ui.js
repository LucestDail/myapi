// ===========================================
// UI Module - Common UI Functions
// ===========================================

import { uiState, saveUiState } from './state.js';

/**
 * Show toast notification
 */
export function showToast(message, type = 'info') {
    const container = document.getElementById('toast-container');
    const toast = document.createElement('div');
    toast.className = `toast ${type}`;
    toast.textContent = message;
    container.appendChild(toast);
    
    setTimeout(() => {
        toast.remove();
    }, 3000);
}

/**
 * Toggle section collapse state
 */
export function toggleSection(sectionName) {
    const section = document.getElementById(`${sectionName}-section`);
    if (section) {
        section.classList.toggle('collapsed');
        uiState.sections[sectionName].collapsed = section.classList.contains('collapsed');
        saveUiState();
    }
}

/**
 * Apply section states from uiState
 */
export function applySectionStates() {
    Object.keys(uiState.sections).forEach(section => {
        const el = document.getElementById(`${section}-section`);
        if (el && uiState.sections[section].collapsed) {
            el.classList.add('collapsed');
        }
    });
}

/**
 * Set dashboard mode
 */
export function setDashboardMode(mode) {
    uiState.dashboardMode = mode;
    saveUiState();
    applyDashboardMode();
}

/**
 * Apply dashboard mode visibility
 */
export function applyDashboardMode() {
    const mode = uiState.dashboardMode;
    
    // Update tab buttons
    document.querySelectorAll('.mode-tab').forEach(tab => {
        tab.classList.toggle('active', tab.dataset.mode === mode);
    });

    // Show/hide sections based on mode
    document.querySelectorAll('.section[data-mode]').forEach(section => {
        const sectionMode = section.dataset.mode;
        if (sectionMode === mode) {
            section.classList.add('mode-visible');
        } else {
            section.classList.remove('mode-visible');
        }
    });
}

/**
 * Update time display
 */
export function updateTime() {
    const timeEl = document.getElementById('current-time');
    if (!timeEl) return;
    
    const now = new Date();
    
    // Format date manually: 2026년1월18일(일) 13:15:32
    const year = now.getFullYear();
    const month = now.getMonth() + 1;
    const day = now.getDate();
    const weekdays = ['일', '월', '화', '수', '목', '금', '토'];
    const weekday = weekdays[now.getDay()];
    
    // Format time as HH:mm:ss
    const hours = String(now.getHours()).padStart(2, '0');
    const minutes = String(now.getMinutes()).padStart(2, '0');
    const seconds = String(now.getSeconds()).padStart(2, '0');
    const timeStr = `${hours}:${minutes}:${seconds}`;
    
    // Format: 2026년1월18일(일) 13:15:32 (공백 포함)
    const formatted = `${year}년${month}월${day}일(${weekday}) ${timeStr}`;
    
    // Apply flip animation
    // 공백을 HTML 엔티티로 변환하여 렌더링 보장
    const chars = formatted.split('').map(char => char === ' ' ? '\u00A0' : char);
    const currentText = timeEl.textContent;
    
    let html = '';
    chars.forEach((char, i) => {
        const prevChar = currentText[i];
        const isChanged = prevChar !== char;
        html += `<span class="flip-char ${isChanged ? 'flipping' : ''}">${char}</span>`;
    });
    
    timeEl.innerHTML = html;
}

/**
 * Switch modal tab
 */
export function switchTab(tabName) {
    // Update tab buttons
    document.querySelectorAll('.modal-tab').forEach(tab => {
        tab.classList.toggle('active', tab.dataset.tab === tabName);
    });
    
    // Update tab content
    document.querySelectorAll('.tab-content').forEach(content => {
        content.classList.toggle('active', content.id === `tab-${tabName}`);
    });
    
    return tabName;
}

/**
 * Open settings modal
 */
export function openSettings() {
    const modal = document.getElementById('settings-modal');
    if (modal) {
        modal.classList.add('active');
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
 * Update connection status indicator
 */
export function updateConnectionStatus(connected) {
    const dot = document.getElementById('status-dot');
    const text = document.getElementById('status-text');
    
    if (dot) {
        dot.className = `status-dot ${connected ? 'connected' : 'disconnected'}`;
    }
    if (text) {
        text.textContent = connected ? '연결됨' : '연결 끊김';
    }
}
