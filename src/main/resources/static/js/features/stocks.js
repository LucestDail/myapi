// ===========================================
// Stocks Feature Module
// ===========================================

import { 
    uiState, saveUiState, stocksData, prevValues,
    stockHighlightIndex, setStockHighlightIndex,
    stockHighlightTimer, setStockHighlightTimer,
    stockNewsData, setStockNewsData
} from '../state.js';
import { formatSectionTime, formatNewsDate, escapeHtml } from '../utils.js';

/**
 * Render stocks list
 */
export function renderStocks() {
    const container = document.getElementById('stocks-container');
    const timeEl = document.getElementById('stocks-time');

    if (!container) return;

    if (!stocksData.quotes || stocksData.quotes.length === 0) {
        container.innerHTML = '<div class="no-data">데이터 없음</div>';
        return;
    }

    let quotes = [...stocksData.quotes];

    // Apply filter
    if (uiState.stocks.filter === 'up') {
        quotes = quotes.filter(s => s.change > 0);
    } else if (uiState.stocks.filter === 'down') {
        quotes = quotes.filter(s => s.change < 0);
    }

    // Apply sorting
    if (uiState.stocks.sortBy !== 'default') {
        quotes.sort((a, b) => {
            const aVal = a[uiState.stocks.sortBy] || 0;
            const bVal = b[uiState.stocks.sortBy] || 0;
            return uiState.stocks.sortOrder === 'asc' ? aVal - bVal : bVal - aVal;
        });
    }

    // Move favorites to top
    const favorites = uiState.stocks.favorites;
    quotes.sort((a, b) => {
        const aFav = favorites.includes(a.symbol) ? 1 : 0;
        const bFav = favorites.includes(b.symbol) ? 1 : 0;
        return bFav - aFav;
    });

    // Get current highlighted symbol
    const highlightedSymbol = getHighlightedSymbol();

    const html = quotes.map((stock) => {
        const price = stock.currentPrice != null ? stock.currentPrice.toFixed(2) : 'N/A';
        const change = stock.change != null ? stock.change.toFixed(2) : '0.00';
        const percent = stock.percentChange != null ? stock.percentChange.toFixed(2) : '0.00';
        const isPositive = stock.change >= 0;
        const sign = isPositive ? '+' : '';
        const changeClass = isPositive ? 'positive' : 'negative';
        const isFavorite = favorites.includes(stock.symbol);
        const isHighlighted = stock.symbol === highlightedSymbol;

        return `
            <div class="stock-item ${isHighlighted ? 'highlighted' : ''}" data-symbol="${stock.symbol}" onclick="selectStock('${stock.symbol}')">
                <span class="stock-favorite ${isFavorite ? 'active' : ''}" onclick="event.stopPropagation(); toggleStockFavorite('${stock.symbol}')">${isFavorite ? '★' : '☆'}</span>
                <span class="stock-symbol">${stock.symbol}</span>
                <span class="stock-name">${stock.name || ''}</span>
                <span class="stock-price" id="price-${stock.symbol}">$${price}</span>
                <span class="stock-change ${changeClass}" id="change-${stock.symbol}">${sign}${change} (${sign}${percent}%)</span>
            </div>
        `;
    }).join('');
    
    // Check for price changes and apply flip animation
    quotes.forEach(stock => {
        const priceKey = `price-${stock.symbol}`;
        const price = stock.currentPrice != null ? stock.currentPrice.toFixed(2) : 'N/A';
        const prevPrice = prevValues[priceKey];
        
        if (prevPrice && prevPrice !== price) {
            setTimeout(() => {
                const priceEl = document.getElementById(priceKey);
                if (priceEl) {
                    priceEl.classList.add('flipping');
                    setTimeout(() => priceEl.classList.remove('flipping'), 300);
                }
            }, 10);
        }
        prevValues[priceKey] = price;
    });

    container.innerHTML = html;
    if (timeEl) {
        timeEl.textContent = formatSectionTime(stocksData.fetchedAt);
    }
}

/**
 * Set stock filter
 */
export function setStockFilter(filter) {
    uiState.stocks.filter = filter;
    document.querySelectorAll('.section-controls [data-filter]').forEach(btn => {
        btn.classList.toggle('active', btn.dataset.filter === filter);
    });
    saveUiState();
    renderStocks();
}

/**
 * Toggle stock sort
 */
export function toggleStockSort() {
    if (uiState.stocks.sortBy === 'default') {
        uiState.stocks.sortBy = 'percentChange';
    } else if (uiState.stocks.sortBy === 'percentChange' && uiState.stocks.sortOrder === 'desc') {
        uiState.stocks.sortOrder = 'asc';
    } else {
        uiState.stocks.sortBy = 'default';
        uiState.stocks.sortOrder = 'desc';
    }
    
    const sortBtn = document.querySelector('[data-sort="percentChange"]');
    if (sortBtn) {
        if (uiState.stocks.sortBy === 'default') {
            sortBtn.textContent = '정렬: 변화율';
            sortBtn.classList.remove('active');
        } else {
            sortBtn.textContent = `정렬: 변화율 ${uiState.stocks.sortOrder === 'desc' ? '↓' : '↑'}`;
            sortBtn.classList.add('active');
        }
    }
    
    saveUiState();
    renderStocks();
}

/**
 * Toggle stock favorite
 */
export function toggleStockFavorite(symbol) {
    const idx = uiState.stocks.favorites.indexOf(symbol);
    if (idx === -1) {
        uiState.stocks.favorites.push(symbol);
    } else {
        uiState.stocks.favorites.splice(idx, 1);
    }
    saveUiState();
    renderStocks();
}

/**
 * Get highlighted symbol
 */
export function getHighlightedSymbol() {
    if (!stocksData.quotes || stocksData.quotes.length === 0) return null;
    const idx = stockHighlightIndex % stocksData.quotes.length;
    return stocksData.quotes[idx]?.symbol;
}

/**
 * Start stock highlight rotation
 */
export function startStockHighlight() {
    if (stockHighlightTimer) {
        clearInterval(stockHighlightTimer);
    }
    
    if (!uiState.stocks.autoHighlight) return;
    
    const interval = (uiState.stocks.highlightInterval || 10) * 1000;
    const timer = setInterval(() => {
        if (stocksData.quotes && stocksData.quotes.length > 0) {
            setStockHighlightIndex((stockHighlightIndex + 1) % stocksData.quotes.length);
            updateStockHighlight();
        }
    }, interval);
    setStockHighlightTimer(timer);
    
    // Load news for initial stock
    setTimeout(() => {
        if (stocksData.quotes && stocksData.quotes.length > 0) {
            updateStockHighlight();
        }
    }, 1000);
}

/**
 * Update stock highlight
 */
export function updateStockHighlight() {
    const symbol = getHighlightedSymbol();
    if (!symbol) return;
    
    // Update highlight in UI
    document.querySelectorAll('.stock-item').forEach(item => {
        item.classList.toggle('highlighted', item.dataset.symbol === symbol);
    });
    
    // Fetch news for highlighted stock
    loadStockNews(symbol);
}

/**
 * Select stock manually
 */
export function selectStock(symbol) {
    const idx = stocksData.quotes?.findIndex(s => s.symbol === symbol);
    if (idx !== -1) {
        setStockHighlightIndex(idx);
        updateStockHighlight();
        
        if (uiState.stocks.autoHighlight) {
            startStockHighlight();
        }
    }
}

/**
 * Load stock news
 */
export async function loadStockNews(symbol) {
    const tickerEl = document.getElementById('stock-news-ticker');
    const listEl = document.getElementById('stock-news-list');
    const timeEl = document.getElementById('stock-news-time');
    
    if (!tickerEl || !listEl) return;
    
    tickerEl.textContent = symbol;
    listEl.innerHTML = '<div class="stock-news-empty">뉴스 로딩 중...</div>';
    
    try {
        const response = await fetch(`/api/rss/yahoo/stock?symbol=${symbol}`);
        if (!response.ok) throw new Error('Failed to fetch news');
        
        const data = await response.json();
        setStockNewsData(data.items || []);
        
        if (stockNewsData.length === 0) {
            listEl.innerHTML = '<div class="stock-news-empty">관련 뉴스가 없습니다</div>';
            return;
        }
        
        // Display top 5 news
        const newsHtml = stockNewsData.slice(0, 5).map(news => {
            const pubDate = news.pubDate ? formatNewsDate(news.pubDate) : '';
            return `
                <a class="stock-news-item" href="${news.link}" target="_blank" rel="noopener noreferrer">
                    <div class="stock-news-title">${escapeHtml(news.title)}</div>
                    <div class="stock-news-meta">
                        <span>${news.source || 'Yahoo Finance'}</span>
                        <span>${pubDate}</span>
                    </div>
                </a>
            `;
        }).join('');
        
        listEl.innerHTML = newsHtml;
        if (timeEl) {
            timeEl.textContent = formatSectionTime(data.fetchedAt);
        }
        
    } catch (error) {
        console.error('Failed to load stock news:', error);
        listEl.innerHTML = '<div class="stock-news-empty">뉴스를 불러올 수 없습니다</div>';
    }
}
