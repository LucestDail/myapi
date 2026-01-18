// ===========================================
// App Entry Point - Main Application
// ===========================================

// Core imports
import { loadUiState } from './state.js';
import { 
    showToast, toggleSection, applySectionStates, 
    setDashboardMode, applyDashboardMode, updateTime,
    switchTab
} from './ui.js';
import { loadConfig, initSSERenderers } from './sse.js';

// Feature imports
import { 
    renderStocks, setStockFilter, toggleStockSort, toggleStockFavorite,
    startStockHighlight, selectStock
} from './features/stocks.js';
import { renderWeather, toggleWeatherFavorite } from './features/weather.js';
import { 
    loadLifeInfo, renderWorldClock, renderDdayList, openDdayModal, 
    deleteDday, loadQuote 
} from './features/lifeinfo.js';
import { 
    renderNews, toggleNewsAutoSlide, startNewsAutoSlide, 
    stopNewsAutoSlide, changeNewsPage 
} from './features/news.js';
import { 
    loadTodos, setTodoFilter, addTodo, handleTodoKeypress,
    toggleTodoComplete, deleteTodo, clearCompletedTodos 
} from './features/todo.js';
import { 
    initTimer, setTimerMode, startTimer, pauseTimer, 
    resumeTimer, resetTimer, updateTimerDuration
} from './features/timer.js';
import { 
    renderFocusStats, loadMemo, handleMemoInput, 
    renderBookmarks, openBookmarkModal, deleteBookmark 
} from './features/productivity.js';
import { renderSystem, loadSystemHistory, setHistoryPeriod } from './features/system.js';
import { 
    openSettings, closeSettings, saveSettings, 
    addTicker, updateTicker, removeTicker, renderTickerList,
    toggleAlertRule, deleteAlertRule, showAddRuleForm, 
    cancelRuleForm, saveAlertRule 
} from './features/settings.js';

// ===========================================
// Initialization
// ===========================================
document.addEventListener('DOMContentLoaded', () => {
    // Initialize SSE renderers (to avoid circular dependency)
    initSSERenderers(renderStocks, renderWeather, renderNews, renderSystem);
    
    // Load state and config
    loadUiState();
    loadConfig();
    
    // Start time updates
    updateTime();
    setInterval(updateTime, 1000);
    
    // Apply initial UI states
    applySectionStates();
    applyDashboardMode();
    
    // Initialize features
    loadTodos();
    initTimer();
    loadLifeInfo();
    loadSystemHistory();
    startStockHighlight();
    
    // New features init
    renderWorldClock();
    setInterval(renderWorldClock, 1000);
    renderDdayList();
    loadQuote();
    renderFocusStats();
    loadMemo();
    renderBookmarks();
    
    // Periodic refreshes
    setInterval(loadLifeInfo, 10 * 60 * 1000);  // 10 minutes
    setInterval(loadSystemHistory, 60 * 1000);   // 1 minute
});

// ===========================================
// Expose Functions Globally (for onclick handlers)
// ===========================================

// UI Functions
window.toggleSection = toggleSection;
window.setDashboardMode = setDashboardMode;
window.showToast = showToast;
window.switchTab = switchTab;

// Stocks Functions
window.setStockFilter = setStockFilter;
window.toggleStockSort = toggleStockSort;
window.toggleStockFavorite = toggleStockFavorite;
window.selectStock = selectStock;

// Weather Functions
window.toggleWeatherFavorite = toggleWeatherFavorite;

// Life Info Functions
window.openDdayModal = openDdayModal;
window.deleteDday = deleteDday;
window.loadQuote = loadQuote;

// News Functions
window.toggleNewsAutoSlide = toggleNewsAutoSlide;
window.changeNewsPage = changeNewsPage;

// Todo Functions
window.setTodoFilter = setTodoFilter;
window.addTodo = addTodo;
window.handleTodoKeypress = handleTodoKeypress;
window.toggleTodoComplete = toggleTodoComplete;
window.deleteTodo = deleteTodo;
window.clearCompletedTodos = clearCompletedTodos;

// Timer Functions
window.setTimerMode = setTimerMode;
window.startTimer = startTimer;
window.pauseTimer = pauseTimer;
window.resumeTimer = resumeTimer;
window.resetTimer = resetTimer;
window.updateTimerDuration = updateTimerDuration;

// Productivity Functions
window.handleMemoInput = handleMemoInput;
window.openBookmarkModal = openBookmarkModal;
window.deleteBookmark = deleteBookmark;

// System Functions
window.setHistoryPeriod = setHistoryPeriod;

// Settings Functions
window.openSettings = openSettings;
window.closeSettings = closeSettings;
window.saveSettings = saveSettings;
window.addTicker = addTicker;
window.updateTicker = updateTicker;
window.removeTicker = removeTicker;
window.toggleAlertRule = toggleAlertRule;
window.deleteAlertRule = deleteAlertRule;
window.showAddRuleForm = showAddRuleForm;
window.cancelRuleForm = cancelRuleForm;
window.saveAlertRule = saveAlertRule;

console.log('Dashboard Application Initialized');
