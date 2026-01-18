// ===========================================
// News Feature Module
// ===========================================

import { 
    uiState, saveUiState, yahooNewsData, yonhapNewsData, 
    newsSlideIndex, setNewsSlideIndex, newsSlideTimer, setNewsSlideTimer,
    newsPageIndex, setNewsPageIndex, NEWS_PER_PAGE
} from '../state.js';

/**
 * Render news
 */
export function renderNews() {
    const container = document.getElementById('news-container');
    if (!container) return;

    let html = '';

    // Helper function to get news items based on mode
    function getNewsForDisplay(newsArray, count) {
        if (uiState.news.autoSlide) {
            if (newsArray.length <= count) return newsArray.slice(0, count);
            const startIdx = newsSlideIndex % newsArray.length;
            const result = [];
            for (let i = 0; i < Math.min(count, newsArray.length); i++) {
                result.push(newsArray[(startIdx + i) % newsArray.length]);
            }
            return result;
        } else {
            const startIdx = newsPageIndex * count;
            return newsArray.slice(startIdx, startIdx + count);
        }
    }

    function getPaginationInfo(newsArray, count) {
        const totalPages = Math.ceil(newsArray.length / count);
        const currentPage = newsPageIndex + 1;
        return { totalPages, currentPage, hasNext: currentPage < totalPages, hasPrev: currentPage > 1 };
    }

    // Yahoo News
    const yahooPagination = getPaginationInfo(yahooNewsData, NEWS_PER_PAGE);
    html += `<div class="news-section-title">
        <span>YAHOO FINANCE</span>
        ${uiState.news.autoSlide 
            ? '<span class="news-auto-slide">▶ 자동</span>' 
            : yahooNewsData.length > NEWS_PER_PAGE 
                ? `<span class="news-pagination">
                    <button class="page-btn" onclick="changeNewsPage(-1)" ${!yahooPagination.hasPrev ? 'disabled' : ''}>◀</button>
                    <span class="page-info">${yahooPagination.currentPage}/${yahooPagination.totalPages}</span>
                    <button class="page-btn" onclick="changeNewsPage(1)" ${!yahooPagination.hasNext ? 'disabled' : ''}>▶</button>
                  </span>` 
                : ''}
    </div>`;

    const displayedYahooNews = getNewsForDisplay(yahooNewsData, NEWS_PER_PAGE);
    displayedYahooNews.forEach((news, idx) => {
        const isHighlight = uiState.news.autoSlide && idx === 0;
        html += `
            <div class="news-item ${isHighlight ? 'news-highlight' : ''}" onclick="window.open('${news.link}', '_blank')">
                <div class="news-title">${news.title}</div>
                <div class="news-meta">
                    <span>${news.pubDate || ''}</span>
                </div>
            </div>
        `;
    });

    // Yonhap News
    if (yonhapNewsData.length > 0) {
        html += '<div class="news-section-title"><span>연합뉴스</span></div>';
        yonhapNewsData.slice(0, 3).forEach(news => {
            html += `
                <div class="news-item" onclick="window.open('${news.link}', '_blank')">
                    <div class="news-title">${news.title}</div>
                    <div class="news-meta">
                        <span>${news.pubDate || ''}</span>
                    </div>
                </div>
            `;
        });
    }

    container.innerHTML = html || '<div class="no-data">뉴스 없음</div>';
}

/**
 * Toggle news auto slide
 */
export function toggleNewsAutoSlide() {
    const checkbox = document.getElementById('news-auto-slide');
    uiState.news.autoSlide = checkbox.checked;
    saveUiState();
    
    if (uiState.news.autoSlide) {
        startNewsAutoSlide();
    } else {
        stopNewsAutoSlide();
        setNewsPageIndex(0);
    }
    renderNews();
}

/**
 * Start news auto slide
 */
export function startNewsAutoSlide() {
    stopNewsAutoSlide();
    const interval = (uiState.news.slideInterval || 5) * 1000;
    const timer = setInterval(() => {
        setNewsSlideIndex(newsSlideIndex + 1);
        renderNews();
    }, interval);
    setNewsSlideTimer(timer);
}

/**
 * Stop news auto slide
 */
export function stopNewsAutoSlide() {
    if (newsSlideTimer) {
        clearInterval(newsSlideTimer);
        setNewsSlideTimer(null);
    }
}

/**
 * Change news page
 */
export function changeNewsPage(delta) {
    const totalPages = Math.ceil(yahooNewsData.length / NEWS_PER_PAGE);
    const newPage = newsPageIndex + delta;
    if (newPage >= 0 && newPage < totalPages) {
        setNewsPageIndex(newPage);
        renderNews();
    }
}
