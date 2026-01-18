// ===========================================
// State Management Module
// ===========================================

// User ID for API calls
export let userId = localStorage.getItem('userId');
if (!userId) {
    userId = crypto.randomUUID();
    localStorage.setItem('userId', userId);
}

// Config
export let config = null;
export function setConfig(newConfig) {
    config = newConfig;
}

// SSE
export let eventSource = null;
export let reconnectAttempts = 0;
export const MAX_RECONNECT_ATTEMPTS = 10;
export function setEventSource(es) {
    eventSource = es;
}
export function setReconnectAttempts(count) {
    reconnectAttempts = count;
}

// UI State
export let uiState = {
    dashboardMode: 'all',
    sections: {
        stocks: { collapsed: false },
        weather: { collapsed: false },
        lifeinfo: { collapsed: false },
        worldclock: { collapsed: false },
        dday: { collapsed: false },
        quote: { collapsed: false },
        news: { collapsed: false },
        todo: { collapsed: false },
        timer: { collapsed: false },
        focus: { collapsed: false },
        memo: { collapsed: false },
        bookmarks: { collapsed: false },
        system: { collapsed: false }
    },
    stocks: {
        filter: 'all',
        sortBy: 'default',
        sortOrder: 'desc',
        favorites: [],
        highlightInterval: 10,
        autoHighlight: true
    },
    weather: {
        favorites: []
    },
    news: {
        autoSlide: false,
        slideInterval: 5
    },
    todo: {
        filter: 'all'
    },
    timer: {
        mode: 'timer',
        duration: 25
    },
    system: {
        cpuWarning: 70,
        cpuDanger: 90,
        memWarning: 70,
        memDanger: 90
    }
};

// Data State
export let stocksData = { quotes: [] };
export let weatherData = [];
export let yahooNewsData = [];
export let yonhapNewsData = [];
export let newsSlideIndex = 0;
export let newsSlideTimer = null;
export let newsPageIndex = 0;
export const NEWS_PER_PAGE = 5;
export let todosData = [];
export let lifeInfoData = null;
export let systemHistoryData = [];
export let historyPeriod = '1h';
export let alertRulesData = [];
export let editingRuleId = null;

// Timer State
export let timerData = null;
export let timerInterval = null;
export let timerRemainingSeconds = 0;
export const POMODORO_WORK_DURATION = 25 * 60;
export const POMODORO_BREAK_DURATION = 5 * 60;
export const TIMER_CIRCLE_CIRCUMFERENCE = 2 * Math.PI * 62;

// Stock Highlight State
export let stockHighlightIndex = 0;
export let stockHighlightTimer = null;
export let stockNewsData = [];

// New Features State
export let ddayList = JSON.parse(localStorage.getItem('ddayList') || '[]');
export let bookmarksList = JSON.parse(localStorage.getItem('bookmarksList') || '[]');
export let memoContent = localStorage.getItem('memoContent') || '';
export let memoSaveTimeout = null;
export let focusStats = JSON.parse(localStorage.getItem('focusStats') || '{"today":0,"todayDate":"","week":0,"weekStart":"","total":0}');

// World Clock cities
export const worldClockCities = [
    { name: '서울', timezone: 'Asia/Seoul', flag: '🇰🇷' },
    { name: '뉴욕', timezone: 'America/New_York', flag: '🇺🇸' },
    { name: '런던', timezone: 'Europe/London', flag: '🇬🇧' },
    { name: '도쿄', timezone: 'Asia/Tokyo', flag: '🇯🇵' },
    { name: '시드니', timezone: 'Australia/Sydney', flag: '🇦🇺' },
    { name: '파리', timezone: 'Europe/Paris', flag: '🇫🇷' }
];

// Setters for mutable state
export function setStocksData(data) { stocksData = data; }
export function setWeatherData(data) { weatherData = data; }
export function setYahooNewsData(data) { yahooNewsData = data; }
export function setYonhapNewsData(data) { yonhapNewsData = data; }
export function setNewsSlideIndex(idx) { newsSlideIndex = idx; }
export function setNewsSlideTimer(timer) { newsSlideTimer = timer; }
export function setNewsPageIndex(idx) { newsPageIndex = idx; }
export function setTodosData(data) { todosData = data; }
export function setLifeInfoData(data) { lifeInfoData = data; }
export function setSystemHistoryData(data) { systemHistoryData = data; }
export function setHistoryPeriod(period) { historyPeriod = period; }
export function setAlertRulesData(data) { alertRulesData = data; }
export function setEditingRuleId(id) { editingRuleId = id; }
export function setTimerData(data) { timerData = data; }
export function setTimerInterval(interval) { timerInterval = interval; }
export function setTimerRemainingSeconds(seconds) { timerRemainingSeconds = seconds; }
export function setStockHighlightIndex(idx) { stockHighlightIndex = idx; }
export function setStockHighlightTimer(timer) { stockHighlightTimer = timer; }
export function setStockNewsData(data) { stockNewsData = data; }
export function setDdayList(list) { ddayList = list; localStorage.setItem('ddayList', JSON.stringify(list)); }
export function setBookmarksList(list) { bookmarksList = list; localStorage.setItem('bookmarksList', JSON.stringify(list)); }
export function setMemoContent(content) { memoContent = content; }
export function setMemoSaveTimeout(timeout) { memoSaveTimeout = timeout; }
export function setFocusStats(stats) { focusStats = stats; localStorage.setItem('focusStats', JSON.stringify(stats)); }

// Previous values for flip animation
export const prevValues = {};

// Load UI state from localStorage
export function loadUiState() {
    const saved = localStorage.getItem('dashboardUiState');
    if (saved) {
        try {
            const parsed = JSON.parse(saved);
            uiState = { ...uiState, ...parsed };
        } catch (e) {
            console.error('Failed to parse UI state:', e);
        }
    }
}

// Save UI state to localStorage
export function saveUiState() {
    localStorage.setItem('dashboardUiState', JSON.stringify(uiState));
}
