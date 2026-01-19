// ===========================================
// SSE (Server-Sent Events) Module
// ===========================================

import { 
    userId, config, setConfig, eventSource, setEventSource, 
    reconnectAttempts, setReconnectAttempts, MAX_RECONNECT_ATTEMPTS,
    setStocksData, setWeatherData, setYahooNewsData, setYonhapNewsData
} from './state.js';
import { showToast } from './ui.js';
import { formatSectionTime } from './utils.js';

// Feature modules will be imported dynamically to avoid circular dependencies
let renderStocks, renderWeather, renderNews, renderSystem;

// Initialize feature imports (called from app.js)
export function initSSERenderers(stocks, weather, news, system) {
    renderStocks = stocks;
    renderWeather = weather;
    renderNews = news;
    renderSystem = system;
}

/**
 * Load config and initialize SSE connection
 */
export async function loadConfig() {
    try {
        const response = await fetch('/api/dashboard/config', {
            headers: { 'X-User-Id': userId }
        });
        const configData = await response.json();
        setConfig(configData);
        if (configData.youtubeUrl) {
            updateYouTubePlayer(configData.youtubeUrl);
        }
        connectSSE();
    } catch (error) {
        console.error('Failed to load config:', error);
        const defaultConfig = {
            youtubeUrl: 'https://www.youtube.com/watch?v=jfKfPfyJRdk',
            tickers: [
                { symbol: 'SPY', name: 'S&P500' },
                { symbol: 'QLD', name: 'NAS2X' },
                { symbol: 'NVDA', name: 'NVIDIA' }
            ]
        };
        setConfig(defaultConfig);
        updateYouTubePlayer(defaultConfig.youtubeUrl);
        connectSSE();
    }
}

/**
 * Update YouTube player with video URL
 */
export function updateYouTubePlayer(url) {
    const videoId = extractYouTubeId(url);
    const player = document.getElementById('youtube-player');
    if (videoId && player) {
        player.src = `https://www.youtube.com/embed/${videoId}?autoplay=1&mute=0`;
    }
}

/**
 * Extract YouTube video ID from URL
 */
export function extractYouTubeId(url) {
    if (!url) return null;
    const match = url.match(/(?:youtube\.com\/watch\?v=|youtu\.be\/|youtube\.com\/embed\/)([^&\s?]+)/);
    return match ? match[1] : null;
}

/**
 * Connect to SSE stream
 */
export function connectSSE() {
    // Close existing connection
    if (eventSource) {
        eventSource.close();
    }

    updateConnectionStatus('connecting');
    // EventSource는 헤더를 설정할 수 없으므로 쿼리 파라미터로 userId 전달
    const newEventSource = new EventSource(`/api/dashboard/stream?userId=${encodeURIComponent(userId)}`);
    setEventSource(newEventSource);

    newEventSource.onopen = () => {
        setReconnectAttempts(0);
        updateConnectionStatus('connected');
    };

    newEventSource.addEventListener('dashboard', (event) => {
        const data = JSON.parse(event.data);
        console.log('[SSE] Received dashboard data:', data);
        if (data.stocks && data.stocks.quotes) {
            console.log('[SSE] Stocks quotes received:', data.stocks.quotes.length, 'items');
        }
        handleDashboardData(data);
    });

    newEventSource.addEventListener('system', (event) => {
        const data = JSON.parse(event.data);
        if (data.system) {
            renderSystem(data.system, data.timestamp);
        }
    });

    newEventSource.addEventListener('alert', (event) => {
        const data = JSON.parse(event.data);
        showToast(data.message, data.severity || 'info');
    });

    newEventSource.onerror = () => {
        updateConnectionStatus('disconnected');
        newEventSource.close();

        if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
            setReconnectAttempts(reconnectAttempts + 1);
            const delay = Math.min(1000 * Math.pow(2, reconnectAttempts), 30000);
            setTimeout(connectSSE, delay);
        }
    };
}

/**
 * Handle incoming dashboard data
 */
function handleDashboardData(data) {
    console.log('[SSE] handleDashboardData called with:', {
        hasStocks: !!data.stocks,
        stocksCount: data.stocks?.quotes?.length || 0,
        hasWeather: !!data.weather,
        hasNews: !!data.news,
        hasSystem: !!data.system
    });
    
    if (data.stocks) {
        console.log('[SSE] Processing stocks data:', data.stocks.quotes?.length, 'quotes');
        setStocksData(data.stocks);
        console.log('[SSE] Stocks data set, calling renderStocks');
        renderStocks();
    }
    if (data.weather) {
        setWeatherData(data.weather);
        renderWeather();
    }
    if (data.news) {
        setYahooNewsData(data.news.yahooNews || []);
        setYonhapNewsData(data.news.yonhapNews || []);
        renderNews();
        const newsTimeEl = document.getElementById('news-time');
        if (newsTimeEl) {
            newsTimeEl.textContent = formatSectionTime(data.news.fetchedAt);
        }
    }
    if (data.system) {
        renderSystem(data.system, data.timestamp);
    }
}

/**
 * Update connection status UI
 */
function updateConnectionStatus(status) {
    const dot = document.getElementById('status-dot');
    const text = document.getElementById('status-text');

    if (dot) {
        dot.className = 'status-dot';
        switch (status) {
            case 'connected':
                dot.classList.add('connected');
                break;
            case 'disconnected':
                dot.classList.add('disconnected');
                break;
        }
    }
    
    if (text) {
        switch (status) {
            case 'connected':
                text.textContent = '연결됨';
                break;
            case 'disconnected':
                text.textContent = '연결 끊김';
                break;
            default:
                text.textContent = '연결 중...';
        }
    }
}
