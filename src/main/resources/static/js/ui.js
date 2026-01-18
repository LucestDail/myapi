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
        if (mode === 'all' || sectionMode === mode) {
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
    const formatted = now.toLocaleTimeString('ko-KR', { 
        hour: '2-digit', 
        minute: '2-digit', 
        second: '2-digit',
        hour12: false
    });
    
    // Apply flip animation
    const chars = formatted.split('');
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
