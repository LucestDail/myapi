// ===========================================
// API Module
// ===========================================

import { userId } from './state.js';

// 통합 nginx 게이트웨이 뒤(/myapi) 또는 단독 실행(루트) 어디서든 동작하도록,
// 절대경로(`/api/...`)를 현재 페이지 base 기준 상대경로(`api/...`)로 정규화한다.
// 브라우저가 자동으로 페이지 디렉터리 prefix 를 붙여준다.
function normalizeEndpoint(endpoint) {
    if (typeof endpoint !== 'string' || !endpoint) return endpoint;
    if (/^https?:\/\//i.test(endpoint)) return endpoint;
    return endpoint.replace(/^\/+/, '');
}

/**
 * Make a GET request to the API
 */
export async function fetchApi(endpoint, options = {}) {
    const headers = {
        'X-User-Id': userId,
        ...options.headers
    };
    
    const response = await fetch(normalizeEndpoint(endpoint), {
        ...options,
        headers
    });
    
    if (!response.ok) {
        throw new Error(`API Error: ${response.status}`);
    }
    
    return response.json();
}

/**
 * Make a POST request to the API
 */
export async function postApi(endpoint, data, options = {}) {
    const headers = {
        'Content-Type': 'application/json',
        'X-User-Id': userId,
        ...options.headers
    };
    
    const response = await fetch(normalizeEndpoint(endpoint), {
        method: 'POST',
        headers,
        body: JSON.stringify(data),
        ...options
    });
    
    if (!response.ok) {
        throw new Error(`API Error: ${response.status}`);
    }
    
    return response.json();
}

/**
 * Make a PUT request to the API
 */
export async function putApi(endpoint, data, options = {}) {
    const headers = {
        'Content-Type': 'application/json',
        'X-User-Id': userId,
        ...options.headers
    };
    
    const response = await fetch(normalizeEndpoint(endpoint), {
        method: 'PUT',
        headers,
        body: JSON.stringify(data),
        ...options
    });
    
    if (!response.ok) {
        throw new Error(`API Error: ${response.status}`);
    }
    
    return response.json();
}

/**
 * Make a DELETE request to the API
 */
export async function deleteApi(endpoint, options = {}) {
    const headers = {
        'X-User-Id': userId,
        ...options.headers
    };
    
    const response = await fetch(normalizeEndpoint(endpoint), {
        method: 'DELETE',
        headers,
        ...options
    });
    
    if (!response.ok) {
        throw new Error(`API Error: ${response.status}`);
    }
    
    // Some DELETE endpoints may not return content
    const text = await response.text();
    return text ? JSON.parse(text) : null;
}

/**
 * Fetch with raw response (for non-JSON responses)
 */
export async function fetchRaw(endpoint, options = {}) {
    const headers = {
        'X-User-Id': userId,
        ...options.headers
    };
    
    return fetch(normalizeEndpoint(endpoint), {
        ...options,
        headers
    });
}
