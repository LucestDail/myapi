// ===========================================
// API Module
// ===========================================

import { userId } from './state.js';

/**
 * Make a GET request to the API
 */
export async function fetchApi(endpoint, options = {}) {
    const headers = {
        'X-User-Id': userId,
        ...options.headers
    };
    
    const response = await fetch(endpoint, {
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
    
    const response = await fetch(endpoint, {
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
    
    const response = await fetch(endpoint, {
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
    
    const response = await fetch(endpoint, {
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
    
    return fetch(endpoint, {
        ...options,
        headers
    });
}
