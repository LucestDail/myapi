// ===========================================
// System Feature Module
// ===========================================

import { 
    uiState, systemHistoryData, setSystemHistoryData, 
    historyPeriod, setHistoryPeriod as setHistoryPeriodState 
} from '../state.js';
import { formatBytes, formatUptime, formatSectionTime } from '../utils.js';

let lastSystemData = null;

/**
 * Render system status
 */
export function renderSystem(systemData, timestamp) {
    const container = document.getElementById('system-container');
    const timeEl = document.getElementById('system-time');

    if (!container) return;

    lastSystemData = systemData;

    const cpuPercent = systemData.cpuUsage >= 0 ? systemData.cpuUsage.toFixed(1) : 'N/A';
    const memPercent = systemData.memoryUsagePercent.toFixed(1);
    const heapPercent = systemData.heapUsagePercent.toFixed(1);
    const uptime = formatUptime(systemData.uptimeMillis);

    const getCpuClass = (val) => val > uiState.system.cpuDanger ? 'danger' : val > uiState.system.cpuWarning ? 'warning' : '';
    const getMemClass = (val) => val > uiState.system.memDanger ? 'danger' : val > uiState.system.memWarning ? 'warning' : '';

    const cpuClass = getCpuClass(systemData.cpuUsage);
    const memClass = getMemClass(systemData.memoryUsagePercent);
    const heapClass = getMemClass(systemData.heapUsagePercent);

    const html = `
        <div class="system-bar">
            <span class="bar-label">CPU</span>
            <div class="bar-container">
                <div class="bar-fill ${cpuClass}" style="width: ${Math.min(systemData.cpuUsage, 100)}%"></div>
            </div>
            <span class="bar-value ${cpuClass}">${cpuPercent}%</span>
        </div>
        <div class="system-bar">
            <span class="bar-label">MEM</span>
            <div class="bar-container">
                <div class="bar-fill ${memClass}" style="width: ${memPercent}%"></div>
            </div>
            <span class="bar-value ${memClass}">${formatBytes(systemData.memoryUsed)}/${formatBytes(systemData.memoryTotal)}</span>
        </div>
        <div class="system-bar">
            <span class="bar-label">HEAP</span>
            <div class="bar-container">
                <div class="bar-fill ${heapClass}" style="width: ${heapPercent}%"></div>
            </div>
            <span class="bar-value ${heapClass}">${formatBytes(systemData.heapUsed)}/${formatBytes(systemData.heapMax)}</span>
        </div>
        <div class="system-item">
            <span class="system-label">THR</span>
            <span class="system-value">${systemData.threadCount}</span>
        </div>
        <div class="system-item">
            <span class="system-label">GC</span>
            <span class="system-value">${systemData.gcCount}/${systemData.gcTime}ms</span>
        </div>
        <div class="system-item">
            <span class="system-label">UP</span>
            <span class="system-value">${uptime}</span>
        </div>
    `;

    container.innerHTML = html;
    if (timeEl) {
        timeEl.textContent = formatSectionTime(timestamp || new Date());
    }
    
    // Update extended system info
    updateSystemExtended(systemData);
}

/**
 * Update extended system info (disk, uptime, network)
 */
export async function updateSystemExtended(systemData) {
    // Fetch full system status for disk info
    try {
        const response = await fetch('/api/system/status');
        if (response.ok) {
            const fullStatus = await response.json();
            updateDiskInfo(fullStatus.disks);
            updateNetworkInfo();
        }
    } catch (error) {
        console.error('Failed to fetch system status:', error);
    }

    // Server uptime
    const uptimeEl = document.getElementById('server-uptime');
    if (uptimeEl && systemData) {
        uptimeEl.innerHTML = `
            <div style="font-size: 12px; font-weight: 600;">${formatUptime(systemData.uptimeMillis)}</div>
        `;
    }
}

/**
 * Update disk information
 */
function updateDiskInfo(disks) {
    const diskUsedEl = document.getElementById('disk-used');
    const diskBarEl = document.getElementById('disk-bar');
    if (!diskUsedEl || !diskBarEl || !disks) return;
    
    // Get the primary disk (usually "/" or "C:\")
    const diskEntries = Object.entries(disks);
    if (diskEntries.length === 0) return;
    
    // Find the root disk
    const rootDisk = diskEntries.find(([path]) => path === '/' || path === 'C:\\') || diskEntries[0];
    const diskInfo = rootDisk[1];
    
    const diskPercent = Math.round(diskInfo.usagePercent);
    const usedGB = formatBytes(diskInfo.usedSpace);
    const totalGB = formatBytes(diskInfo.totalSpace);
    const freeGB = formatBytes(diskInfo.freeSpace);
    
    diskUsedEl.innerHTML = `
        <div style="font-size: 12px; font-weight: 600; margin-bottom: 4px;">${diskPercent}%</div>
        <div style="font-size: 9px; color: var(--text-secondary); line-height: 1.4;">
            <div>사용: ${usedGB}</div>
            <div>전체: ${totalGB}</div>
            <div>여유: ${freeGB}</div>
        </div>
    `;
    diskBarEl.style.width = `${diskPercent}%`;
    diskBarEl.style.background = diskPercent > 90 ? 'var(--accent-red)' : diskPercent > 70 ? 'var(--accent-yellow)' : 'var(--accent-cyan)';
}

/**
 * Update network information
 */
let lastNetworkCheck = Date.now();
let networkSpeed = null;

function updateNetworkInfo() {
    const networkDotEl = document.getElementById('network-dot');
    const networkTextEl = document.getElementById('network-text');
    if (!networkDotEl || !networkTextEl) return;
    
    const online = navigator.onLine;
    networkDotEl.className = `network-dot ${online ? 'online' : 'offline'}`;
    
    if (online) {
        // Try to measure network speed
        measureNetworkSpeed().then(speed => {
            if (speed) {
                networkTextEl.innerHTML = `
                    <div style="font-size: 12px; font-weight: 600; margin-bottom: 4px;">Online</div>
                    <div style="font-size: 9px; color: var(--text-secondary);">
                        지연: ${speed}
                    </div>
                `;
            } else {
                networkTextEl.innerHTML = `
                    <div style="font-size: 12px; font-weight: 600;">Online</div>
                `;
            }
        });
    } else {
        networkTextEl.innerHTML = `
            <div style="font-size: 12px; font-weight: 600; color: var(--accent-red);">Offline</div>
        `;
    }
}

/**
 * Measure network speed (simple ping test)
 */
async function measureNetworkSpeed() {
    const now = Date.now();
    if (now - lastNetworkCheck < 5000) {
        return networkSpeed; // Return cached value
    }
    lastNetworkCheck = now;
    
    try {
        const startTime = performance.now();
        const response = await fetch('/api/system/status', { cache: 'no-cache' });
        const endTime = performance.now();
        
        if (response.ok) {
            const latency = Math.round(endTime - startTime);
            networkSpeed = `${latency}ms`;
            return networkSpeed;
        }
    } catch (error) {
        // Ignore errors
    }
    
    return null;
}

/**
 * Load system history
 */
export async function loadSystemHistory() {
    try {
        const response = await fetch(`/api/system/history?period=${historyPeriod}`);
        if (response.ok) {
            setSystemHistoryData(await response.json());
            renderSystemChart();
        }
    } catch (error) {
        console.error('Failed to load system history:', error);
    }
}

/**
 * Set history period
 */
export function setHistoryPeriod(period) {
    setHistoryPeriodState(period);
    document.querySelectorAll('.history-period-btn').forEach(btn => {
        btn.classList.toggle('active', btn.dataset.period === period);
    });
    loadSystemHistory();
}

/**
 * Render system chart
 */
export function renderSystemChart() {
    const canvas = document.getElementById('system-chart');
    if (!canvas) return;

    const ctx = canvas.getContext('2d');
    const container = canvas.parentElement;
    
    const rect = container.getBoundingClientRect();
    canvas.width = rect.width * window.devicePixelRatio;
    canvas.height = rect.height * window.devicePixelRatio;
    ctx.scale(window.devicePixelRatio, window.devicePixelRatio);
    
    const width = rect.width;
    const height = rect.height;
    const padding = { top: 10, right: 10, bottom: 20, left: 35 };
    const chartWidth = width - padding.left - padding.right;
    const chartHeight = height - padding.top - padding.bottom;

    ctx.clearRect(0, 0, width, height);

    if (!systemHistoryData || systemHistoryData.length === 0) {
        ctx.fillStyle = '#666';
        ctx.font = '10px JetBrains Mono';
        ctx.textAlign = 'center';
        ctx.fillText('데이터 없음', width / 2, height / 2);
        return;
    }

    // Draw grid lines
    ctx.strokeStyle = '#3d3d3d';
    ctx.lineWidth = 0.5;
    for (let i = 0; i <= 4; i++) {
        const y = padding.top + (chartHeight / 4) * i;
        ctx.beginPath();
        ctx.moveTo(padding.left, y);
        ctx.lineTo(width - padding.right, y);
        ctx.stroke();
        
        ctx.fillStyle = '#666';
        ctx.font = '9px JetBrains Mono';
        ctx.textAlign = 'right';
        ctx.fillText(`${100 - i * 25}%`, padding.left - 5, y + 3);
    }

    const points = systemHistoryData.length;
    // points가 0이거나 1이면 xStep이 제대로 계산되지 않을 수 있음
    const xStep = points > 1 ? chartWidth / (points - 1) : chartWidth;

    const drawLine = (data, key, color) => {
        ctx.strokeStyle = color;
        ctx.lineWidth = 1.5;
        ctx.beginPath();
        
        data.forEach((item, i) => {
            const x = padding.left + i * xStep;
            const value = item[key] || 0;
            const y = padding.top + chartHeight - (value / 100) * chartHeight;
            
            if (i === 0) ctx.moveTo(x, y);
            else ctx.lineTo(x, y);
        });
        
        ctx.stroke();
    };

    drawLine(systemHistoryData, 'cpuUsage', '#00d4ff');
    drawLine(systemHistoryData, 'memoryUsagePercent', '#ffd700');
    drawLine(systemHistoryData, 'heapUsagePercent', '#00ff88');

    // Time labels
    ctx.fillStyle = '#666';
    ctx.font = '8px JetBrains Mono';
    ctx.textAlign = 'center';

    const labelCount = Math.min(5, points);
    const labelStep = Math.floor(points / labelCount);
    
    for (let i = 0; i < labelCount; i++) {
        const idx = i * labelStep;
        if (idx < points) {
            const item = systemHistoryData[idx];
            const x = padding.left + idx * xStep;
            const time = new Date(item.timestamp);
            const label = historyPeriod === '7d' 
                ? `${time.getMonth() + 1}/${time.getDate()}`
                : `${String(time.getHours()).padStart(2, '0')}:${String(time.getMinutes()).padStart(2, '0')}`;
            
            ctx.fillText(label, x, height - 5);
        }
    }

    setupChartHover(canvas, padding, chartWidth, chartHeight, xStep);
}

/**
 * Setup chart hover interaction
 */
function setupChartHover(canvas, padding, chartWidth, chartHeight, xStep) {
    const tooltip = document.getElementById('chart-tooltip');
    if (!tooltip) return;
    
    canvas.onmousemove = (e) => {
        const rect = canvas.getBoundingClientRect();
        const x = e.clientX - rect.left;
        const y = e.clientY - rect.top;

        if (x >= padding.left && x <= padding.left + chartWidth &&
            y >= padding.top && y <= padding.top + chartHeight) {
            
            const index = Math.round((x - padding.left) / xStep);
            if (index >= 0 && index < systemHistoryData.length) {
                const item = systemHistoryData[index];
                const time = new Date(item.timestamp);
                
                tooltip.innerHTML = `
                    <div style="color: var(--accent-cyan)">CPU: ${item.cpuUsage?.toFixed(1) || 'N/A'}%</div>
                    <div style="color: var(--accent-yellow)">MEM: ${item.memoryUsagePercent?.toFixed(1) || 'N/A'}%</div>
                    <div style="color: var(--accent-green)">HEAP: ${item.heapUsagePercent?.toFixed(1) || 'N/A'}%</div>
                    <div style="color: var(--text-muted); font-size: 8px">${time.toLocaleTimeString('ko-KR')}</div>
                `;
                tooltip.style.left = `${e.clientX - rect.left + 10}px`;
                tooltip.style.top = `${e.clientY - rect.top - 10}px`;
                tooltip.classList.add('visible');
            }
        } else {
            tooltip.classList.remove('visible');
        }
    };

    canvas.onmouseleave = () => {
        tooltip.classList.remove('visible');
    };
}
