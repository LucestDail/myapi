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
    closeDdayModal, saveDday, deleteDday, loadQuote 
} from './features/lifeinfo.js';
import { 
    renderNews, toggleNewsAutoSlide, startNewsAutoSlide, 
    stopNewsAutoSlide, changeNewsPage, initNewsAutoSlide
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
    renderBookmarks, openBookmarkModal, closeBookmarkModal, 
    saveBookmark, previewBookmark, deleteBookmark 
} from './features/productivity.js';
import { renderSystem, loadSystemHistory, setHistoryPeriod } from './features/system.js';
import { 
    initSocial, loadSocialNews, loadTraffic, loadEmergency,
    renderSocialNews, renderTraffic, renderEmergency,
    toggleSocialNewsAutoSlide, startSocialNewsAutoSlide, stopSocialNewsAutoSlide, changeSocialNewsPage,
    toggleTrafficAutoSlide, startTrafficAutoSlide, stopTrafficAutoSlide, changeTrafficPage,
    toggleEmergencyAutoSlide, changeEmergencyPage
} from './features/social.js';
import { 
    openSettings, closeSettings, saveSettings, 
    addTicker, updateTicker, removeTicker, renderTickerList,
    toggleAlertRule, deleteAlertRule, showAddRuleForm, 
    cancelRuleForm, saveAlertRule 
} from './features/settings.js';
import { 
    openAIReportModal, closeAIReportModal, generateAIReport
} from './features/ai-report.js';

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
    initNewsAutoSlide();
    initSocial(); // Initialize social features (news, traffic, emergency)
    
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
window.closeDdayModal = closeDdayModal;
window.saveDday = saveDday;
window.deleteDday = deleteDday;
window.loadQuote = loadQuote;

// News Functions
window.toggleNewsAutoSlide = toggleNewsAutoSlide;
window.changeNewsPage = changeNewsPage;

// Social News Functions
window.toggleSocialNewsAutoSlide = toggleSocialNewsAutoSlide;
window.changeSocialNewsPage = changeSocialNewsPage;

// Traffic Functions
window.toggleTrafficAutoSlide = toggleTrafficAutoSlide;
window.changeTrafficPage = changeTrafficPage;

// Emergency Functions
window.toggleEmergencyAutoSlide = toggleEmergencyAutoSlide;
window.changeEmergencyPage = changeEmergencyPage;

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
window.closeBookmarkModal = closeBookmarkModal;
window.saveBookmark = saveBookmark;
window.previewBookmark = previewBookmark;
window.deleteBookmark = deleteBookmark;

// System Functions
window.setHistoryPeriod = setHistoryPeriod;

// Settings Functions
window.openSettings = openSettings;
window.closeSettings = closeSettings;
window.saveSettings = saveSettings;

// AI Report Functions
window.openAIReportModal = openAIReportModal;
window.closeAIReportModal = closeAIReportModal;
window.generateAIReport = generateAIReport;
window.addTicker = addTicker;
window.updateTicker = updateTicker;
window.removeTicker = removeTicker;
window.toggleAlertRule = toggleAlertRule;
window.deleteAlertRule = deleteAlertRule;
window.showAddRuleForm = showAddRuleForm;
window.cancelRuleForm = cancelRuleForm;
window.saveAlertRule = saveAlertRule;

console.log('Dashboard Application Initialized');
