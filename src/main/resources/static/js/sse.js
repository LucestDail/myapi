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
        const response = await fetch('api/dashboard/config', {
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
 * 지금 즉시 대시보드를 다시 그린다.
 *
 * 🔴 왜 필요한가: 설정 저장 뒤 화면 갱신을 SSE 재연결에만 맡기고 있었다. 재연결은
 *    비동기라 언제 첫 페이로드가 오는지 보장이 없고, 오는 도중 사용자가 모달을 닫으면
 *    "저장했는데 안 바뀐다"로 보인다(실제로 티커 추가가 새로고침해야 반영됐다).
 *    서버는 저장 직후부터 새 값을 내주므로(확인함), 여기서 한 번 당겨오면 결정적으로 반영된다.
 *    SSE 재연결은 그대로 두되 — 이후 주기 갱신을 받아야 하니 — 첫 그림은 이쪽이 책임진다.
 */
export async function refreshDashboardNow() {
    try {
        const response = await fetch('api/dashboard/data', {
            headers: { 'X-User-Id': userId }
        });
        if (!response.ok) throw new Error('HTTP ' + response.status);
        handleDashboardData(await response.json());
    } catch (error) {
        // 실패해도 치명적이지 않다 — 뒤따르는 SSE 재연결이 결국 같은 데이터를 준다.
        console.warn('[SSE] Immediate dashboard refresh failed, falling back to SSE:', error);
    }
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
    const currentUserId = userId; // userId가 변경되지 않도록 현재 값 저장
    console.log('[SSE] Connecting with userId:', currentUserId);
    const newEventSource = new EventSource(`api/dashboard/stream?userId=${encodeURIComponent(currentUserId)}`);
    setEventSource(newEventSource);

    newEventSource.onopen = () => {
        setReconnectAttempts(0);
        updateConnectionStatus('connected');
        console.log('[SSE] Connection opened with userId:', currentUserId);
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
        // 토스트는 항상 띄운다. 브라우저 알림은 권한·설정에 따라 안 뜰 수 있는데,
        // 그때 아무것도 안 보이면 사용자는 알림이 안 온 줄 안다.
        showToast(data.message, data.severity || 'info');
        if (Array.isArray(data.channels) && data.channels.includes('browser')) {
            showBrowserNotification(data);
        }
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
 * OS 데스크톱 알림 (Web Notifications).
 *
 * 규칙에 channels=sse,browser 로 켠 알림만 여기로 온다. 서버는 채널과 무관하게 SSE 로
 * 내려보내고, 이 함수는 "화면을 안 보고 있어도 알게 하는" 부분만 맡는다.
 *
 * 권한이 거부돼 있으면 아무 일도 하지 않는다 — 호출한 쪽이 토스트를 이미 띄웠다.
 */
export function showBrowserNotification(data) {
    if (typeof Notification === 'undefined') {
        console.warn('[alert] 이 브라우저는 데스크톱 알림을 지원하지 않습니다');
        return;
    }
    if (Notification.permission === 'granted') {
        spawnNotification(data);
        return;
    }
    if (Notification.permission === 'denied') {
        // 조용히 사라지지 않게 남긴다. 사용자가 브라우저 설정에서 되돌려야 한다.
        console.warn('[alert] 데스크톱 알림 권한이 거부돼 있어 토스트로만 표시합니다');
        return;
    }
    Notification.requestPermission().then((permission) => {
        if (permission === 'granted') {
            spawnNotification(data);
        } else {
            console.warn('[alert] 데스크톱 알림 권한을 얻지 못했습니다:', permission);
        }
    }).catch((e) => console.warn('[alert] 알림 권한 요청 실패:', e));
}

function spawnNotification(data) {
    try {
        const title = data.severity === 'danger' ? '⚠️ 알림' : '🔔 알림';
        new Notification(title, {
            body: data.message,
            // 같은 규칙의 알림이 연달아 오면 쌓이지 않고 덮어쓰게 한다
            tag: `myapi-alert-${data.type || 'general'}-${data.target || ''}`
        });
    } catch (e) {
        console.warn('[alert] 데스크톱 알림 표시 실패:', e);
    }
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
