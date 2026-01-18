// ===========================================
// Productivity Feature Module (Focus Stats, Memo, Bookmarks)
// ===========================================

import { 
    focusStats, setFocusStats, memoContent, setMemoContent,
    memoSaveTimeout, setMemoSaveTimeout, bookmarksList, setBookmarksList
} from '../state.js';
import { getWeekStart } from '../utils.js';
import { showToast } from '../ui.js';

/**
 * Increment pomodoro count
 */
export function incrementPomodoro() {
    const now = new Date();
    const todayStr = now.toISOString().split('T')[0];
    const weekStart = getWeekStart(now).toISOString().split('T')[0];

    // Reset daily count if new day
    if (focusStats.todayDate !== todayStr) {
        focusStats.today = 0;
        focusStats.todayDate = todayStr;
    }

    // Reset weekly count if new week
    if (focusStats.weekStart !== weekStart) {
        focusStats.week = 0;
        focusStats.weekStart = weekStart;
    }

    focusStats.today++;
    focusStats.week++;
    focusStats.total++;

    setFocusStats({ ...focusStats });
    renderFocusStats();
}

/**
 * Render focus stats
 */
export function renderFocusStats() {
    const container = document.getElementById('focus-container');
    if (!container) return;

    const now = new Date();
    const todayStr = now.toISOString().split('T')[0];
    const weekStart = getWeekStart(now).toISOString().split('T')[0];

    // Check for date changes
    let today = focusStats.today;
    let week = focusStats.week;

    if (focusStats.todayDate !== todayStr) {
        today = 0;
    }
    if (focusStats.weekStart !== weekStart) {
        week = 0;
    }

    container.innerHTML = `
        <div class="focus-stats-grid">
            <div class="focus-stat-card highlight">
                <div class="focus-stat-value">${today}</div>
                <div class="focus-stat-label">오늘 뽀모도로</div>
            </div>
            <div class="focus-stat-card">
                <div class="focus-stat-value">${week}</div>
                <div class="focus-stat-label">이번 주</div>
            </div>
            <div class="focus-stat-card">
                <div class="focus-stat-value">${focusStats.total}</div>
                <div class="focus-stat-label">전체</div>
            </div>
            <div class="focus-stat-card">
                <div class="focus-stat-value">${Math.round(focusStats.total * 25 / 60)}h</div>
                <div class="focus-stat-label">총 집중 시간</div>
            </div>
        </div>
    `;
}

/**
 * Load memo content
 */
export function loadMemo() {
    const textarea = document.getElementById('memo-textarea');
    if (textarea) {
        textarea.value = memoContent;
    }
}

/**
 * Handle memo input
 */
export function handleMemoInput(event) {
    const content = event.target.value;
    setMemoContent(content);
    
    const statusEl = document.getElementById('memo-status');
    if (statusEl) {
        statusEl.textContent = '저장 중...';
        statusEl.classList.remove('saved');
    }

    // Debounce save
    if (memoSaveTimeout) clearTimeout(memoSaveTimeout);
    
    const timeout = setTimeout(() => {
        localStorage.setItem('memoContent', content);
        if (statusEl) {
            statusEl.textContent = '저장됨';
            statusEl.classList.add('saved');
        }
    }, 500);
    setMemoSaveTimeout(timeout);
}

/**
 * Render bookmarks
 */
export function renderBookmarks() {
    const container = document.getElementById('bookmarks-container');
    if (!container) return;

    if (bookmarksList.length === 0) {
        container.innerHTML = `
            <button class="bookmark-add-btn" onclick="openBookmarkModal()" style="grid-column: span 2;">
                + 북마크 추가
            </button>
        `;
        return;
    }

    const html = bookmarksList.map((bookmark, index) => {
        const favicon = `https://www.google.com/s2/favicons?domain=${new URL(bookmark.url).hostname}&sz=32`;
        return `
            <a class="bookmark-item" href="${bookmark.url}" target="_blank" rel="noopener noreferrer">
                <img class="bookmark-favicon" src="${favicon}" alt="" onerror="this.src='data:image/svg+xml,<svg xmlns=%22http://www.w3.org/2000/svg%22 viewBox=%220 0 16 16%22><rect fill=%22%23666%22 width=%2216%22 height=%2216%22 rx=%222%22/></svg>'">
                <span class="bookmark-name">${bookmark.name}</span>
                <button onclick="event.preventDefault(); event.stopPropagation(); deleteBookmark(${index});" style="background: none; border: none; color: var(--text-muted); cursor: pointer; font-size: 10px; margin-left: auto;">✕</button>
            </a>
        `;
    }).join('');

    container.innerHTML = html + `
        <button class="bookmark-add-btn" onclick="openBookmarkModal()">+ 추가</button>
    `;
}

/**
 * Open bookmark modal
 */
export function openBookmarkModal() {
    const name = prompt('북마크 이름:');
    if (!name) return;
    
    const url = prompt('URL:');
    if (!url) return;

    try {
        new URL(url);
    } catch {
        showToast('올바른 URL을 입력하세요', 'warning');
        return;
    }

    const newList = [...bookmarksList, { name, url }];
    setBookmarksList(newList);
    renderBookmarks();
    showToast('북마크가 추가되었습니다', 'info');
}

/**
 * Delete bookmark
 */
export function deleteBookmark(index) {
    const newList = [...bookmarksList];
    newList.splice(index, 1);
    setBookmarksList(newList);
    renderBookmarks();
}
