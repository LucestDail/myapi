// ===========================================
// Life Info Feature Module
// ===========================================

import { lifeInfoData, setLifeInfoData, worldClockCities, ddayList, setDdayList } from '../state.js';
import { formatSectionTime, getAirQualityClass } from '../utils.js';
import { showToast } from '../ui.js';

/**
 * Load life info from API
 */
export async function loadLifeInfo() {
    try {
        const response = await fetch('/api/info/summary?location=Seoul&lat=37.5665&lon=126.9780');
        if (response.ok) {
            setLifeInfoData(await response.json());
            renderLifeInfo();
        }
    } catch (error) {
        console.error('Failed to load life info:', error);
        const container = document.getElementById('lifeinfo-container');
        if (container) {
            container.innerHTML = '<div class="no-data">생활정보를 불러올 수 없습니다</div>';
        }
    }
}

/**
 * Render life info
 */
export function renderLifeInfo() {
    const container = document.getElementById('lifeinfo-container');
    const timeEl = document.getElementById('lifeinfo-time');

    if (!container) return;

    if (!lifeInfoData) {
        container.innerHTML = '<div class="no-data">데이터 없음</div>';
        return;
    }

    let html = '';

    // Exchange rates
    if (lifeInfoData.exchange && lifeInfoData.exchange.rates) {
        const rates = lifeInfoData.exchange.rates;
        html += `
            <div class="lifeinfo-row">
                <span class="lifeinfo-icon">💱</span>
                <span class="lifeinfo-label">환율</span>
                <div class="lifeinfo-value">
                    <span class="lifeinfo-item">
                        <span class="currency">USD</span>
                        <span class="value">${rates.KRW ? rates.KRW.toLocaleString() : '-'}</span>
                        <span class="unit">₩</span>
                    </span>
                    <span class="lifeinfo-item">
                        <span class="currency">JPY</span>
                        <span class="value">${rates.JPY ? (rates.KRW / rates.JPY * 100).toFixed(1) : '-'}</span>
                        <span class="unit">₩/100</span>
                    </span>
                </div>
            </div>
        `;
    }

    // Air quality
    if (lifeInfoData.airQuality) {
        const aq = lifeInfoData.airQuality;
        html += `
            <div class="lifeinfo-row">
                <span class="lifeinfo-icon">🌫️</span>
                <span class="lifeinfo-label">대기</span>
                <div class="lifeinfo-value">
                    <span class="lifeinfo-item">
                        <span>PM10</span>
                        <span class="value">${aq.pm10 || '-'}</span>
                        <span class="air-quality-badge ${getAirQualityClass(aq.pm10Grade)}">${aq.pm10Grade || '-'}</span>
                    </span>
                    <span class="lifeinfo-item">
                        <span>PM2.5</span>
                        <span class="value">${aq.pm25 || '-'}</span>
                        <span class="air-quality-badge ${getAirQualityClass(aq.pm25Grade)}">${aq.pm25Grade || '-'}</span>
                    </span>
                </div>
            </div>
        `;
    }

    // Sun times
    if (lifeInfoData.sunTimes) {
        const st = lifeInfoData.sunTimes;
        html += `
            <div class="lifeinfo-row">
                <span class="lifeinfo-icon">🌅</span>
                <span class="lifeinfo-label">일출</span>
                <div class="lifeinfo-value">
                    <span class="lifeinfo-item"><span>🌅</span><span class="sun-time">${st.sunrise || '-'}</span></span>
                    <span class="lifeinfo-item"><span>🌇</span><span class="sun-time">${st.sunset || '-'}</span></span>
                </div>
            </div>
        `;
    }

    // Holidays
    if (lifeInfoData.holiday) {
        const h = lifeInfoData.holiday;
        let holidayContent = h.isToday 
            ? '<span class="holiday-badge">오늘은 공휴일!</span>'
            : h.next?.date 
                ? `<span class="holiday-badge">D-${Math.ceil((new Date(h.next.date) - new Date()) / (1000*60*60*24))}</span> ${h.next.name}`
                : '-';
        
        html += `
            <div class="lifeinfo-row">
                <span class="lifeinfo-icon">📅</span>
                <span class="lifeinfo-label">공휴일</span>
                <div class="lifeinfo-value">${holidayContent}</div>
            </div>
        `;
    }

    container.innerHTML = html || '<div class="no-data">데이터 없음</div>';
    if (timeEl) timeEl.textContent = formatSectionTime(new Date());
}

/**
 * Render world clock
 */
export function renderWorldClock() {
    const container = document.getElementById('worldclock-container');
    if (!container) return;

    const html = worldClockCities.map(city => {
        const now = new Date();
        const options = { timeZone: city.timezone, hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false };
        const dateOptions = { timeZone: city.timezone, month: 'short', day: 'numeric', weekday: 'short' };
        const time = now.toLocaleTimeString('ko-KR', options);
        const date = now.toLocaleDateString('ko-KR', dateOptions);
        
        return `
            <div class="worldclock-item">
                <div class="worldclock-city">${city.flag} ${city.name}</div>
                <div class="worldclock-time">${time}</div>
                <div class="worldclock-date">${date}</div>
            </div>
        `;
    }).join('');
    
    container.innerHTML = html;
}

/**
 * Render D-Day list
 */
export function renderDdayList() {
    const container = document.getElementById('dday-container');
    if (!container) return;

    if (ddayList.length === 0) {
        container.innerHTML = '<div class="no-data" style="font-size: 10px; text-align: center; padding: 12px;">D-Day를 추가해보세요</div>';
        return;
    }

    const today = new Date();
    today.setHours(0, 0, 0, 0);

    const html = ddayList.map((item, index) => {
        const targetDate = new Date(item.date);
        targetDate.setHours(0, 0, 0, 0);
        const diffDays = Math.ceil((targetDate - today) / (1000 * 60 * 60 * 24));
        
        let countText, countClass;
        if (diffDays === 0) { countText = 'D-DAY'; countClass = 'today'; }
        else if (diffDays > 0) { countText = `D-${diffDays}`; countClass = 'future'; }
        else { countText = `D+${Math.abs(diffDays)}`; countClass = 'past'; }

        return `
            <div class="dday-item">
                <div>
                    <div class="dday-title">${item.title}</div>
                    <div class="dday-date">${item.date}</div>
                </div>
                <div style="display: flex; align-items: center; gap: 8px;">
                    <span class="dday-count ${countClass}">${countText}</span>
                    <button onclick="deleteDday(${index})" style="background: none; border: none; color: var(--text-muted); cursor: pointer; font-size: 10px;">✕</button>
                </div>
            </div>
        `;
    }).join('');

    container.innerHTML = html;
}

/**
 * Open D-Day modal
 */
export function openDdayModal() {
    const title = prompt('D-Day 제목:');
    if (!title) return;
    
    const date = prompt('날짜 (YYYY-MM-DD):');
    if (!date || !/^\d{4}-\d{2}-\d{2}$/.test(date)) {
        showToast('올바른 날짜 형식을 입력하세요 (예: 2025-12-25)', 'warning');
        return;
    }

    const newList = [...ddayList, { title, date }].sort((a, b) => new Date(a.date) - new Date(b.date));
    setDdayList(newList);
    renderDdayList();
    showToast('D-Day가 추가되었습니다', 'info');
}

/**
 * Delete D-Day
 */
export function deleteDday(index) {
    const newList = [...ddayList];
    newList.splice(index, 1);
    setDdayList(newList);
    renderDdayList();
}

/**
 * Load quote
 */
export async function loadQuote() {
    const textEl = document.getElementById('quote-text');
    const authorEl = document.getElementById('quote-author');
    
    if (!textEl || !authorEl) return;
    
    try {
        const response = await fetch('https://api.quotable.io/random?tags=inspirational,motivational');
        if (response.ok) {
            const data = await response.json();
            textEl.textContent = `"${data.content}"`;
            authorEl.textContent = `— ${data.author}`;
        } else {
            throw new Error('API failed');
        }
    } catch (error) {
        const fallbackQuotes = [
            { content: "The only way to do great work is to love what you do.", author: "Steve Jobs" },
            { content: "Believe you can and you're halfway there.", author: "Theodore Roosevelt" }
        ];
        const quote = fallbackQuotes[Math.floor(Math.random() * fallbackQuotes.length)];
        textEl.textContent = `"${quote.content}"`;
        authorEl.textContent = `— ${quote.author}`;
    }
}
