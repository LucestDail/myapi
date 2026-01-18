// ===========================================
// Weather Feature Module
// ===========================================

import { uiState, saveUiState, weatherData } from '../state.js';
import { formatSectionTime } from '../utils.js';

const weatherIcons = {
    'Clear': '☀', 'Clouds': '☁', 'Rain': '🌧', 'Drizzle': '🌦',
    'Thunderstorm': '⛈', 'Snow': '❄', 'Mist': '🌫', 'Fog': '🌫', 'Haze': '🌫'
};

/**
 * Render weather list
 */
export function renderWeather() {
    const container = document.getElementById('weather-container');
    const timeEl = document.getElementById('weather-time');

    if (!container) return;

    if (!weatherData || weatherData.length === 0) {
        container.innerHTML = '<div class="no-data">데이터 없음</div>';
        return;
    }

    // Sort favorites to top
    const favorites = uiState.weather.favorites;
    const sorted = [...weatherData].sort((a, b) => {
        const aFav = favorites.includes(a.city) ? 1 : 0;
        const bFav = favorites.includes(b.city) ? 1 : 0;
        return bFav - aFav;
    });

    const html = sorted.map(w => {
        const icon = weatherIcons[w.weather] || '☁';
        const temp = w.temperatureCelsius.toFixed(1);
        const isFavorite = favorites.includes(w.city);
        const isAlert = w.temperatureCelsius < -10 || w.temperatureCelsius > 35;

        return `
            <div class="weather-item ${isAlert ? 'weather-alert' : ''}">
                <span class="weather-favorite ${isFavorite ? 'active' : ''}" onclick="toggleWeatherFavorite('${w.city}')">${isFavorite ? '★' : '☆'}</span>
                <span class="weather-city-name">${w.cityKo || w.city}</span>
                <span class="weather-icon">${icon}</span>
                <span class="weather-temp">${temp}°</span>
                <span class="weather-humidity">${w.humidity}%</span>
            </div>
        `;
    }).join('');

    container.innerHTML = html;
    if (timeEl) {
        timeEl.textContent = formatSectionTime(new Date());
    }
}

/**
 * Toggle weather favorite
 */
export function toggleWeatherFavorite(city) {
    const idx = uiState.weather.favorites.indexOf(city);
    if (idx === -1) {
        uiState.weather.favorites.push(city);
    } else {
        uiState.weather.favorites.splice(idx, 1);
    }
    saveUiState();
    renderWeather();
}
