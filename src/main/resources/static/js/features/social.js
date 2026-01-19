// ===========================================
// Social Feature Module (News, Traffic, Emergency)
// ===========================================

import { 
    uiState, saveUiState, socialNewsData, trafficData, emergencyData,
    setSocialNewsData, setTrafficData, setEmergencyData,
    socialNewsSlideIndex, setSocialNewsSlideIndex, socialNewsSlideTimer, setSocialNewsSlideTimer,
    socialNewsPageIndex, setSocialNewsPageIndex, SOCIAL_NEWS_PER_PAGE,
    trafficSlideIndex, setTrafficSlideIndex, trafficSlideTimer, setTrafficSlideTimer,
    trafficPageIndex, setTrafficPageIndex, TRAFFIC_PER_PAGE,
    emergencySlideIndex, setEmergencySlideIndex, emergencySlideTimer, setEmergencySlideTimer,
    emergencyPageIndex, setEmergencyPageIndex, EMERGENCY_PER_PAGE
} from '../state.js';
import { formatSectionTime, formatNewsDate } from '../utils.js';

/**
 * Render social news
 */
export function renderSocialNews() {
    const container = document.getElementById('social-news-container');
    if (!container) return;

    if (!socialNewsData || socialNewsData.length === 0) {
        container.innerHTML = '<div class="no-data">뉴스 데이터가 없습니다</div>';
        return;
    }

    // Helper function to get news items based on mode
    function getNewsForDisplay(newsArray, count) {
        if (uiState.socialNews.autoSlide) {
            if (newsArray.length <= count) return newsArray.slice(0, count);
            const startIdx = socialNewsSlideIndex % newsArray.length;
            const result = [];
            for (let i = 0; i < Math.min(count, newsArray.length); i++) {
                result.push(newsArray[(startIdx + i) % newsArray.length]);
            }
            return result;
        } else {
            const startIdx = socialNewsPageIndex * count;
            return newsArray.slice(startIdx, startIdx + count);
        }
    }

    function getPaginationInfo(newsArray, count) {
        const totalPages = Math.ceil(newsArray.length / count);
        const currentPage = socialNewsPageIndex + 1;
        return { totalPages, currentPage, hasNext: currentPage < totalPages, hasPrev: currentPage > 1 };
    }

    const pagination = getPaginationInfo(socialNewsData, SOCIAL_NEWS_PER_PAGE);
    let html = '<div class="news-section-title">';
    html += '<span>뉴스</span>';
    if (uiState.socialNews.autoSlide) {
        html += '<span class="news-auto-slide">▶ 자동</span>';
    } else if (socialNewsData.length > SOCIAL_NEWS_PER_PAGE) {
        html += `<span class="news-pagination">
            <button class="page-btn" onclick="changeSocialNewsPage(-1)" ${!pagination.hasPrev ? 'disabled' : ''}>◀</button>
            <span class="page-info">${pagination.currentPage}/${pagination.totalPages}</span>
            <button class="page-btn" onclick="changeSocialNewsPage(1)" ${!pagination.hasNext ? 'disabled' : ''}>▶</button>
        </span>`;
    }
    html += '</div>';

    const displayedNews = getNewsForDisplay(socialNewsData, SOCIAL_NEWS_PER_PAGE);
    displayedNews.forEach((news, idx) => {
        // 날짜 포맷팅 (상대 시간 또는 절대 시간)
        const createDT = news.createDT || '';
        const formattedDate = createDT ? formatNewsDate(createDT) : '';
        const title = news.title || '';
        const content = news.content || '';
        const company = news.company || '';
        const link = news.link || '';
        const isHighlight = uiState.socialNews.autoSlide && idx === 0;
        
        const onClick = link ? `onclick="window.open('${link}', '_blank')"` : '';
        const cursorStyle = link ? 'cursor: pointer;' : '';
        
        html += `
            <div class="social-news-item ${isHighlight ? 'news-highlight' : ''}" ${onClick} style="${cursorStyle}">
                <div class="social-news-header">
                    <span class="social-news-time">${formattedDate || createDT}</span>
                    <span class="social-news-company">${company}</span>
                </div>
                <div class="social-news-title">${title}</div>
                <div class="social-news-content">${content.length > 200 ? content.substring(0, 200) + '...' : content}</div>
            </div>
        `;
    });

    container.innerHTML = html || '<div class="no-data">뉴스 없음</div>';
    
    // Update time
    const timeEl = document.getElementById('social-news-time');
    if (timeEl) {
        timeEl.textContent = formatSectionTime(new Date());
    }
}

/**
 * Render traffic incidents
 */
export function renderTraffic() {
    const container = document.getElementById('traffic-container');
    if (!container) return;

    if (!trafficData || !trafficData.body || !trafficData.body.items || trafficData.body.items.length === 0) {
        container.innerHTML = '<div class="no-data">교통돌발상황 정보가 없습니다</div>';
        return;
    }

    // Sort by date (most recent first) - startDate 형식: "20260118181908" (YYYYMMDDHHmmss)
    const items = [...trafficData.body.items].sort((a, b) => {
        const dateA = a.startDate || '';
        const dateB = b.startDate || '';
        return dateB.localeCompare(dateA); // Descending order (newest first)
    });

    // Helper function to get traffic items based on mode
    function getTrafficForDisplay(trafficArray, count) {
        if (uiState.traffic.autoSlide) {
            if (trafficArray.length <= count) return trafficArray.slice(0, count);
            const startIdx = trafficSlideIndex % trafficArray.length;
            const result = [];
            for (let i = 0; i < Math.min(count, trafficArray.length); i++) {
                result.push(trafficArray[(startIdx + i) % trafficArray.length]);
            }
            return result;
        } else {
            const startIdx = trafficPageIndex * count;
            return trafficArray.slice(startIdx, startIdx + count);
        }
    }

    function getPaginationInfo(trafficArray, count) {
        const totalPages = Math.ceil(trafficArray.length / count);
        const currentPage = trafficPageIndex + 1;
        return { totalPages, currentPage, hasNext: currentPage < totalPages, hasPrev: currentPage > 1 };
    }

    const pagination = getPaginationInfo(items, TRAFFIC_PER_PAGE);
    let html = '<div class="news-section-title">';
    html += '<span>실시간 교통돌발상황</span>';
    if (uiState.traffic.autoSlide) {
        html += '<span class="news-auto-slide">▶ 자동</span>';
    } else if (items.length > TRAFFIC_PER_PAGE) {
        html += `<span class="news-pagination">
            <button class="page-btn" onclick="changeTrafficPage(-1)" ${!pagination.hasPrev ? 'disabled' : ''}>◀</button>
            <span class="page-info">${pagination.currentPage}/${pagination.totalPages}</span>
            <button class="page-btn" onclick="changeTrafficPage(1)" ${!pagination.hasNext ? 'disabled' : ''}>▶</button>
        </span>`;
    }
    html += '</div>';

    html += '<table class="traffic-table"><thead><tr><th>일시</th><th>도로명</th><th>내용</th></tr></thead><tbody>';
    
    const displayedItems = getTrafficForDisplay(items, TRAFFIC_PER_PAGE);
    displayedItems.forEach(item => {
        // startDate 형식: "20260118181908" -> "2026-01-18 18:19:08"
        let formattedDate = '';
        const startDate = item.startDate || '';
        if (startDate.length === 14) {
            const year = startDate.substring(0, 4);
            const month = startDate.substring(4, 6);
            const day = startDate.substring(6, 8);
            const hour = startDate.substring(8, 10);
            const minute = startDate.substring(10, 12);
            const second = startDate.substring(12, 14);
            formattedDate = `${year}-${month}-${day} ${hour}:${minute}:${second}`;
        } else {
            formattedDate = startDate;
        }
        
        const roadName = item.roadName || '';
        const message = item.message || '';
        
        html += `
            <tr>
                <td>${formattedDate}</td>
                <td>${roadName}</td>
                <td>${message}</td>
            </tr>
        `;
    });
    
    html += '</tbody></table>';
    container.innerHTML = html;
    
    // Update time
    const timeEl = document.getElementById('traffic-time');
    if (timeEl) {
        timeEl.textContent = formatSectionTime(new Date());
    }
}

/**
 * Render emergency alerts
 */
export function renderEmergency() {
    const container = document.getElementById('emergency-container');
    if (!container) return;

    if (!emergencyData || !emergencyData.items || emergencyData.items.length === 0) {
        container.innerHTML = '<div class="no-data">현재 긴급재난문자가 없습니다</div>';
        return;
    }

    // Sort by date (most recent first) - createDate or registerDate 형식: "20260118181908" (YYYYMMDDHHmmss)
    const items = [...emergencyData.items].sort((a, b) => {
        const dateA = a.createDate || a.registerDate || '';
        const dateB = b.createDate || b.registerDate || '';
        return dateB.localeCompare(dateA); // Descending order (newest first)
    });

    // Helper function to get emergency items based on mode
    function getEmergencyForDisplay(emergencyArray, count) {
        if (uiState.emergency && uiState.emergency.autoSlide) {
            if (emergencyArray.length <= count) return emergencyArray.slice(0, count);
            const startIdx = emergencySlideIndex % emergencyArray.length;
            const result = [];
            for (let i = 0; i < Math.min(count, emergencyArray.length); i++) {
                result.push(emergencyArray[(startIdx + i) % emergencyArray.length]);
            }
            return result;
        } else {
            const startIdx = emergencyPageIndex * count;
            return emergencyArray.slice(startIdx, startIdx + count);
        }
    }

    function getPaginationInfo(emergencyArray, count) {
        const totalPages = Math.ceil(emergencyArray.length / count);
        const currentPage = emergencyPageIndex + 1;
        return { totalPages, currentPage, hasNext: currentPage < totalPages, hasPrev: currentPage > 1 };
    }

    // 페이지네이션을 위해 페이지당 표시 개수 설정 (모든 항목 표시하되 자동슬라이드 지원)
    const emergencyPerPage = 20; // 한 번에 표시할 최대 항목 수
    const pagination = getPaginationInfo(items, emergencyPerPage);
    let html = '<div class="news-section-title">';
    html += '<span>실시간 긴급재난문자</span>';
    if (uiState.emergency && uiState.emergency.autoSlide) {
        html += '<span class="news-auto-slide">▶ 자동</span>';
    } else if (items.length > emergencyPerPage) {
        html += `<span class="news-pagination">
            <button class="page-btn" onclick="changeEmergencyPage(-1)" ${!pagination.hasPrev ? 'disabled' : ''}>◀</button>
            <span class="page-info">${pagination.currentPage}/${pagination.totalPages}</span>
            <button class="page-btn" onclick="changeEmergencyPage(1)" ${!pagination.hasNext ? 'disabled' : ''}>▶</button>
        </span>`;
    }
    html += '</div>';

    html += '<table class="emergency-table"><thead><tr><th>일시</th><th>지역</th><th>내용</th><th>분류</th><th>상세</th></tr></thead><tbody>';
    
    // 모든 항목 표시 (자동슬라이드가 켜져있으면 슬라이드, 아니면 페이지네이션)
    const displayedItems = getEmergencyForDisplay(items, emergencyPerPage);
    displayedItems.forEach(item => {
        // createDate 또는 registerDate 형식: "20260118181908" -> "2026-01-18 18:19:08"
        let formattedDate = '';
        const dateStr = item.createDate || item.registerDate || '';
        if (dateStr.length === 14) {
            const year = dateStr.substring(0, 4);
            const month = dateStr.substring(4, 6);
            const day = dateStr.substring(6, 8);
            const hour = dateStr.substring(8, 10);
            const minute = dateStr.substring(10, 12);
            const second = dateStr.substring(12, 14);
            formattedDate = `${year}-${month}-${day} ${hour}:${minute}:${second}`;
        } else {
            formattedDate = dateStr;
        }
        
        const location = item.locationName || '';
        const content = item.msg || '';
        const category = item.category || item.emergencyStep || '';
        const detail = item.detail || '';
        
        html += `
            <tr>
                <td>${formattedDate}</td>
                <td>${location}</td>
                <td>${content}</td>
                <td>${category}</td>
                <td>${detail}</td>
            </tr>
        `;
    });
    
    html += '</tbody></table>';
    container.innerHTML = html;
    
    // Update time
    const timeEl = document.getElementById('emergency-time');
    if (timeEl) {
        timeEl.textContent = formatSectionTime(new Date());
    }
}

/**
 * Load social news from API
 */
export async function loadSocialNews() {
    try {
        const response = await fetch('/api/social/news');
        const data = await response.json();
        if (data.data && data.data.items) {
            setSocialNewsData(data.data.items);
            renderSocialNews();
        }
    } catch (error) {
        console.error('Failed to load social news:', error);
    }
}

/**
 * Load traffic data from API
 */
export async function loadTraffic() {
    try {
        const response = await fetch('/api/social/traffic');
        const data = await response.json();
        setTrafficData(data);
        renderTraffic();
    } catch (error) {
        console.error('Failed to load traffic data:', error);
    }
}

/**
 * Load emergency data from API
 */
export async function loadEmergency() {
    try {
        const response = await fetch('/api/social/emergency');
        const data = await response.json();
        setEmergencyData(data);
        renderEmergency();
    } catch (error) {
        console.error('Failed to load emergency data:', error);
    }
}

/**
 * Toggle social news auto slide
 */
export function toggleSocialNewsAutoSlide() {
    const checkbox = document.getElementById('social-news-auto-slide');
    if (checkbox) {
        uiState.socialNews.autoSlide = checkbox.checked;
        saveUiState();
        
        if (uiState.socialNews.autoSlide) {
            startSocialNewsAutoSlide();
        } else {
            stopSocialNewsAutoSlide();
            setSocialNewsPageIndex(0);
        }
        renderSocialNews();
    }
}

/**
 * Start social news auto slide
 */
export function startSocialNewsAutoSlide() {
    stopSocialNewsAutoSlide();
    const interval = (uiState.socialNews.slideInterval || 5) * 1000;
    const timer = setInterval(() => {
        setSocialNewsSlideIndex(socialNewsSlideIndex + 1);
        renderSocialNews();
    }, interval);
    setSocialNewsSlideTimer(timer);
}

/**
 * Stop social news auto slide
 */
export function stopSocialNewsAutoSlide() {
    if (socialNewsSlideTimer) {
        clearInterval(socialNewsSlideTimer);
        setSocialNewsSlideTimer(null);
    }
}

/**
 * Change social news page
 */
export function changeSocialNewsPage(delta) {
    const totalPages = Math.ceil(socialNewsData.length / SOCIAL_NEWS_PER_PAGE);
    const newPage = socialNewsPageIndex + delta;
    if (newPage >= 0 && newPage < totalPages) {
        setSocialNewsPageIndex(newPage);
        renderSocialNews();
    }
}

/**
 * Toggle traffic auto slide
 */
export function toggleTrafficAutoSlide() {
    const checkbox = document.getElementById('traffic-auto-slide');
    if (checkbox) {
        uiState.traffic.autoSlide = checkbox.checked;
        saveUiState();
        
        if (uiState.traffic.autoSlide) {
            startTrafficAutoSlide();
        } else {
            stopTrafficAutoSlide();
            setTrafficPageIndex(0);
        }
        renderTraffic();
    }
}

/**
 * Start traffic auto slide
 */
export function startTrafficAutoSlide() {
    stopTrafficAutoSlide();
    const interval = (uiState.traffic.slideInterval || 5) * 1000;
    const timer = setInterval(() => {
        if (trafficData && trafficData.body && trafficData.body.items) {
            setTrafficSlideIndex(trafficSlideIndex + 1);
            renderTraffic();
        }
    }, interval);
    setTrafficSlideTimer(timer);
}

/**
 * Stop traffic auto slide
 */
export function stopTrafficAutoSlide() {
    if (trafficSlideTimer) {
        clearInterval(trafficSlideTimer);
        setTrafficSlideTimer(null);
    }
}

/**
 * Change traffic page
 */
export function changeTrafficPage(delta) {
    if (!trafficData || !trafficData.body || !trafficData.body.items) return;
    // Sort by date before calculating pages
    const items = [...trafficData.body.items].sort((a, b) => {
        const dateA = a.startDate || '';
        const dateB = b.startDate || '';
        return dateB.localeCompare(dateA);
    });
    const totalPages = Math.ceil(items.length / TRAFFIC_PER_PAGE);
    const newPage = trafficPageIndex + delta;
    if (newPage >= 0 && newPage < totalPages) {
        setTrafficPageIndex(newPage);
        renderTraffic();
    }
}

/**
 * Initialize social news auto slide checkbox
 */
export function initSocialNewsAutoSlide() {
    const checkbox = document.getElementById('social-news-auto-slide');
    if (checkbox) {
        checkbox.checked = uiState.socialNews.autoSlide;
        if (uiState.socialNews.autoSlide) {
            startSocialNewsAutoSlide();
        }
    }
}

/**
 * Initialize traffic auto slide checkbox
 */
export function initTrafficAutoSlide() {
    const checkbox = document.getElementById('traffic-auto-slide');
    if (checkbox) {
        checkbox.checked = uiState.traffic.autoSlide;
        if (uiState.traffic.autoSlide) {
            startTrafficAutoSlide();
        }
    }
}

/**
 * Toggle emergency auto slide
 */
export function toggleEmergencyAutoSlide() {
    const checkbox = document.getElementById('emergency-auto-slide');
    if (checkbox) {
        if (!uiState.emergency) {
            uiState.emergency = { autoSlide: false, slideInterval: 5 };
        }
        uiState.emergency.autoSlide = checkbox.checked;
        saveUiState();
        
        if (uiState.emergency.autoSlide) {
            startEmergencyAutoSlide();
        } else {
            stopEmergencyAutoSlide();
            setEmergencyPageIndex(0);
        }
        renderEmergency();
    }
}

/**
 * Start emergency auto slide
 */
export function startEmergencyAutoSlide() {
    stopEmergencyAutoSlide();
    const interval = (uiState.emergency?.slideInterval || 5) * 1000;
    const timer = setInterval(() => {
        if (emergencyData && emergencyData.items) {
            setEmergencySlideIndex(emergencySlideIndex + 1);
            renderEmergency();
        }
    }, interval);
    setEmergencySlideTimer(timer);
}

/**
 * Stop emergency auto slide
 */
export function stopEmergencyAutoSlide() {
    if (emergencySlideTimer) {
        clearInterval(emergencySlideTimer);
        setEmergencySlideTimer(null);
    }
}

/**
 * Change emergency page
 */
export function changeEmergencyPage(delta) {
    if (!emergencyData || !emergencyData.items) return;
    // Sort by date before calculating pages
    const items = [...emergencyData.items].sort((a, b) => {
        const dateA = a.createDate || a.registerDate || '';
        const dateB = b.createDate || b.registerDate || '';
        return dateB.localeCompare(dateA);
    });
    const emergencyPerPage = 20; // 페이지당 항목 수 (renderEmergency와 동일한 값)
    const totalPages = Math.ceil(items.length / emergencyPerPage);
    const newPage = emergencyPageIndex + delta;
    if (newPage >= 0 && newPage < totalPages) {
        setEmergencyPageIndex(newPage);
        renderEmergency();
    }
}

/**
 * Initialize emergency auto slide checkbox
 */
export function initEmergencyAutoSlide() {
    const checkbox = document.getElementById('emergency-auto-slide');
    if (checkbox) {
        if (!uiState.emergency) {
            uiState.emergency = { autoSlide: false, slideInterval: 5 };
        }
        checkbox.checked = uiState.emergency.autoSlide;
        if (uiState.emergency.autoSlide) {
            startEmergencyAutoSlide();
        }
    }
}

/**
 * Initialize social features
 */
export function initSocial() {
    loadSocialNews();
    loadTraffic();
    loadEmergency();
    
    // Initialize auto slide checkboxes
    initSocialNewsAutoSlide();
    initTrafficAutoSlide();
    initEmergencyAutoSlide();
    
    // Set up periodic refresh (5 minutes for traffic and emergency, 1 hour for news)
    setInterval(loadTraffic, 5 * 60 * 1000);
    setInterval(loadEmergency, 5 * 60 * 1000);
    setInterval(loadSocialNews, 60 * 60 * 1000); // 1 hour for news
}
