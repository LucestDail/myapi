// ===========================================
// Utility Functions Module
// ===========================================

/**
 * Format bytes to human-readable string
 */
export function formatBytes(bytes) {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
}

/**
 * Format uptime milliseconds to human-readable string
 */
export function formatUptime(millis) {
    const seconds = Math.floor(millis / 1000);
    const days = Math.floor(seconds / 86400);
    const hours = Math.floor((seconds % 86400) / 3600);
    const minutes = Math.floor((seconds % 3600) / 60);
    
    if (days > 0) {
        return `${days}d ${hours}h`;
    } else if (hours > 0) {
        return `${hours}h ${minutes}m`;
    } else {
        return `${minutes}m`;
    }
}

/**
 * Format date for section time display
 */
export function formatSectionTime(dateInput) {
    let date = dateInput;
    if (typeof dateInput === 'string') {
        date = new Date(dateInput);
    }
    if (!date || isNaN(date.getTime())) {
        return '';
    }
    return date.toLocaleTimeString('ko-KR', { 
        hour: '2-digit', 
        minute: '2-digit',
        hour12: false
    });
}

/**
 * Escape HTML special characters
 */
export function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

/**
 * Format news date to relative time or absolute time
 */
export function formatNewsDate(dateStr) {
    if (!dateStr || dateStr.trim() === '') {
        return '';
    }
    
    try {
        let date;
        
        // "yyyy-MM-dd HH:mm:ss" 형식 파싱
        if (dateStr.match(/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/)) {
            // "yyyy-MM-dd HH:mm:ss" 형식 - 한국 시간대(Asia/Seoul, UTC+9)로 파싱
            // MySQL에서 가져온 시간은 한국 시간대로 저장되어 있으므로,
            // 한국 시간대로 Date 객체를 생성해야 함
            const [datePart, timePart] = dateStr.split(' ');
            const [year, month, day] = datePart.split('-');
            const [hours, minutes, seconds] = timePart.split(':');
            
            // 한국 시간대(UTC+9)로 Date 객체 생성
            // 서버에서 "2026-01-19 10:00:00" (KST)를 전송했다면,
            // 이것을 UTC로 변환하면 "2026-01-19 01:00:00" (UTC)가 됨
            // Date.UTC로 UTC 시간을 만들고, 이것을 Date 객체로 생성
            // 하지만 우리가 원하는 것은 한국 시간대 "2026-01-19 10:00:00"을 그대로 해석하는 것
            // 따라서: 한국 시간대를 UTC로 변환한 값을 저장
            const kstOffset = 9 * 60 * 60 * 1000; // 한국 시간대는 UTC+9 (밀리초)
            const utcTime = Date.UTC(
                parseInt(year), 
                parseInt(month) - 1, 
                parseInt(day), 
                parseInt(hours), 
                parseInt(minutes), 
                parseInt(seconds || 0)
            );
            // 한국 시간대를 UTC로 변환: KST = UTC + 9시간이므로, UTC = KST - 9시간
            // 따라서 UTC 시간에서 9시간을 빼면 한국 시간대가 UTC로 저장된 것처럼 보이지만,
            // 실제로는 한국 시간대를 UTC로 변환한 값이 됨
            // 하지만 이렇게 하면 Date 객체가 UTC로 저장되므로, 
            // getTime()으로 비교할 때는 정확하지만, getHours() 등으로 조회할 때는 로컬 시간대로 변환됨
            // 따라서 더 정확하게는: 한국 시간대를 UTC로 변환한 값을 저장
            // 하지만 실제로는 서버에서 전송한 시간이 한국 시간대라고 가정하고,
            // 클라이언트의 로컬 시간대와 무관하게 한국 시간대로 해석해야 함
            // 따라서: 한국 시간대를 UTC로 변환한 값을 저장
            date = new Date(utcTime - kstOffset);
        } else if (dateStr.includes('T')) {
            // ISO 형식 (2026-01-19T02:57:00)
            date = new Date(dateStr);
        } else {
            date = new Date(dateStr);
        }
        
        if (isNaN(date.getTime())) {
            // 파싱 실패 시 원본 문자열 반환
            return dateStr;
        }
        
        const now = new Date();
        const diffMs = now.getTime() - date.getTime();
        
        // 미래 날짜인 경우 절대 시간 표시 (YYYY-MM-DD HH:mm)
        if (diffMs < 0) {
            const year = date.getFullYear();
            const month = String(date.getMonth() + 1).padStart(2, '0');
            const day = String(date.getDate()).padStart(2, '0');
            const hours = String(date.getHours()).padStart(2, '0');
            const minutes = String(date.getMinutes()).padStart(2, '0');
            return `${year}-${month}-${day} ${hours}:${minutes}`;
        }
        
        const diffMins = Math.floor(diffMs / (1000 * 60));
        const diffHours = Math.floor(diffMs / (1000 * 60 * 60));
        const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24));
        
        // 1분 미만이면 "방금 전"
        if (diffMins < 1) {
            return '방금 전';
        }
        
        // 1시간 미만이면 "N분 전"
        if (diffHours < 1) {
            return `${diffMins}분 전`;
        }
        
        // 24시간 미만이면 "N시간 전"
        if (diffDays < 1) {
            return `${diffHours}시간 전`;
        }
        
        // 7일 미만이면 "N일 전"
        if (diffDays < 7) {
            return `${diffDays}일 전`;
        }
        
        // 7일 이상이면 절대 시간 표시 (YYYY-MM-DD HH:mm)
        const year = date.getFullYear();
        const month = String(date.getMonth() + 1).padStart(2, '0');
        const day = String(date.getDate()).padStart(2, '0');
        const hours = String(date.getHours()).padStart(2, '0');
        const minutes = String(date.getMinutes()).padStart(2, '0');
        return `${year}-${month}-${day} ${hours}:${minutes}`;
    } catch (e) {
        console.error('Error formatting date:', dateStr, e);
        return dateStr;
    }
}

/**
 * Get week start date (Monday)
 */
export function getWeekStart(date) {
    const d = new Date(date);
    const day = d.getDay();
    const diff = d.getDate() - day + (day === 0 ? -6 : 1);
    return new Date(d.setDate(diff));
}

/**
 * Render a list into a container with template function
 * Reduces repetitive render code across components
 */
export function renderList(containerId, data, templateFn, emptyMessage = '데이터 없음') {
    const container = document.getElementById(containerId);
    if (!container) return;
    
    if (!data || data.length === 0) {
        container.innerHTML = `<div class="no-data">${emptyMessage}</div>`;
        return;
    }
    
    container.innerHTML = data.map(templateFn).join('');
}

/**
 * Debounce function
 */
export function debounce(func, wait) {
    let timeout;
    return function executedFunction(...args) {
        const later = () => {
            clearTimeout(timeout);
            func(...args);
        };
        clearTimeout(timeout);
        timeout = setTimeout(later, wait);
    };
}

/**
 * Get weather icon from weather code
 */
export function getWeatherIcon(code) {
    const icons = {
        '01d': '☀️', '01n': '🌙',
        '02d': '⛅', '02n': '☁️',
        '03d': '☁️', '03n': '☁️',
        '04d': '☁️', '04n': '☁️',
        '09d': '🌧️', '09n': '🌧️',
        '10d': '🌦️', '10n': '🌧️',
        '11d': '⛈️', '11n': '⛈️',
        '13d': '🌨️', '13n': '🌨️',
        '50d': '🌫️', '50n': '🌫️'
    };
    return icons[code] || '🌡️';
}

/**
 * Get air quality badge class from grade
 */
export function getAirQualityClass(grade) {
    const gradeMap = {
        '좋음': 'good',
        '보통': 'moderate',
        '나쁨': 'bad',
        '매우나쁨': 'very-bad'
    };
    return gradeMap[grade] || 'moderate';
}
