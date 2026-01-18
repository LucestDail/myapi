// ===========================================
// Todo Feature Module
// ===========================================

import { userId, uiState, saveUiState, todosData, setTodosData } from '../state.js';
import { showToast } from '../ui.js';

/**
 * Load todos from API
 */
export async function loadTodos() {
    try {
        const filter = uiState.todo.filter === 'all' ? '' : `?filter=${uiState.todo.filter}`;
        const response = await fetch(`/api/todos${filter}`, {
            headers: { 'X-User-Id': userId }
        });
        if (response.ok) {
            setTodosData(await response.json());
            renderTodos();
        }
    } catch (error) {
        console.error('Failed to load todos:', error);
    }
}

/**
 * Render todos
 */
export function renderTodos() {
    const container = document.getElementById('todo-container');
    const statsEl = document.getElementById('todo-stats');
    
    if (!container) return;

    if (!todosData || todosData.length === 0) {
        container.innerHTML = '<div class="no-data">할 일이 없습니다</div>';
        if (statsEl) statsEl.innerHTML = '';
        return;
    }

    const html = todosData.map(todo => {
        const priorityClass = todo.priority === 'HIGH' ? 'high' : todo.priority === 'MEDIUM' ? 'medium' : 'low';
        const completedClass = todo.completed ? 'completed' : '';
        
        return `
            <div class="todo-item ${completedClass}">
                <input type="checkbox" class="todo-checkbox" 
                    ${todo.completed ? 'checked' : ''} 
                    onchange="toggleTodoComplete(${todo.id}, this.checked)">
                <div class="todo-priority ${priorityClass}"></div>
                <span class="todo-content">${todo.content}</span>
                <button class="todo-delete" onclick="deleteTodo(${todo.id})">✕</button>
            </div>
        `;
    }).join('');

    container.innerHTML = html;

    // Stats
    const total = todosData.length;
    const completed = todosData.filter(t => t.completed).length;
    const pending = total - completed;
    
    if (statsEl) {
        statsEl.innerHTML = `
            <span>전체: ${total} | 진행중: ${pending} | 완료: ${completed}</span>
            ${completed > 0 ? '<button class="todo-clear-btn" onclick="clearCompletedTodos()">완료 항목 삭제</button>' : ''}
        `;
    }
}

/**
 * Set todo filter
 */
export function setTodoFilter(filter) {
    uiState.todo.filter = filter;
    document.querySelectorAll('[data-todo-filter]').forEach(btn => {
        btn.classList.toggle('active', btn.dataset.todoFilter === filter);
    });
    saveUiState();
    loadTodos();
}

/**
 * Add new todo
 */
export async function addTodo() {
    const input = document.getElementById('todo-input');
    const content = input.value.trim();
    
    if (!content) return;

    try {
        const response = await fetch('/api/todos', {
            method: 'POST',
            headers: { 
                'Content-Type': 'application/json',
                'X-User-Id': userId 
            },
            body: JSON.stringify({ content, priority: 'MEDIUM' })
        });

        if (response.ok) {
            input.value = '';
            loadTodos();
        }
    } catch (error) {
        console.error('Failed to add todo:', error);
        showToast('할 일 추가 실패', 'danger');
    }
}

/**
 * Handle todo input keypress
 */
export function handleTodoKeypress(event) {
    if (event.key === 'Enter') {
        addTodo();
    }
}

/**
 * Toggle todo completion
 */
export async function toggleTodoComplete(id, completed) {
    try {
        await fetch(`/api/todos/${id}`, {
            method: 'PUT',
            headers: { 
                'Content-Type': 'application/json',
                'X-User-Id': userId 
            },
            body: JSON.stringify({ completed })
        });
        loadTodos();
    } catch (error) {
        console.error('Failed to update todo:', error);
    }
}

/**
 * Delete todo
 */
export async function deleteTodo(id) {
    try {
        await fetch(`/api/todos/${id}`, {
            method: 'DELETE',
            headers: { 'X-User-Id': userId }
        });
        loadTodos();
    } catch (error) {
        console.error('Failed to delete todo:', error);
    }
}

/**
 * Clear completed todos
 */
export async function clearCompletedTodos() {
    const completedIds = todosData.filter(t => t.completed).map(t => t.id);
    
    for (const id of completedIds) {
        try {
            await fetch(`/api/todos/${id}`, {
                method: 'DELETE',
                headers: { 'X-User-Id': userId }
            });
        } catch (error) {
            console.error('Failed to delete todo:', error);
        }
    }
    loadTodos();
}
