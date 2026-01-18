package com.example.myapi.service;

import com.example.myapi.dto.productivity.TodoDto;
import com.example.myapi.entity.Todo;
import com.example.myapi.repository.TodoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 할 일 관리 서비스
 */
@Service
public class TodoService {

    private static final Logger log = LoggerFactory.getLogger(TodoService.class);

    private final TodoRepository todoRepository;

    public TodoService(TodoRepository todoRepository) {
        this.todoRepository = todoRepository;
    }

    /**
     * 할 일 목록 조회
     */
    public List<TodoDto> getTodos(String userId) {
        return todoRepository.findByUserIdOrderByPriorityDescCreatedAtDesc(userId).stream()
                .map(TodoDto::from)
                .toList();
    }

    /**
     * 미완료 할 일 목록 조회
     */
    public List<TodoDto> getPendingTodos(String userId) {
        return todoRepository.findByUserIdAndCompletedOrderByPriorityDescCreatedAtDesc(userId, false).stream()
                .map(TodoDto::from)
                .toList();
    }

    /**
     * 완료된 할 일 목록 조회
     */
    public List<TodoDto> getCompletedTodos(String userId) {
        return todoRepository.findByUserIdAndCompletedOrderByPriorityDescCreatedAtDesc(userId, true).stream()
                .map(TodoDto::from)
                .toList();
    }

    /**
     * 미완료 개수 조회
     */
    public long getPendingCount(String userId) {
        return todoRepository.countByUserIdAndCompletedFalse(userId);
    }

    /**
     * 할 일 생성
     */
    @Transactional
    public TodoDto createTodo(String userId, TodoDto dto) {
        Todo entity = dto.toEntity(userId);
        return TodoDto.from(todoRepository.save(entity));
    }

    /**
     * 할 일 수정
     */
    @Transactional
    public TodoDto updateTodo(String userId, Long todoId, TodoDto dto) {
        Todo entity = todoRepository.findById(todoId)
                .filter(t -> t.getUserId().equals(userId))
                .orElseThrow(() -> new IllegalArgumentException("Todo not found: " + todoId));

        if (dto.content() != null) {
            entity.setContent(dto.content());
        }
        if (dto.completed() != null) {
            entity.setCompleted(dto.completed());
        }
        if (dto.priority() != null) {
            entity.setPriority(dto.priority());
        }
        entity.setDueDate(dto.dueDate());

        return TodoDto.from(todoRepository.save(entity));
    }

    /**
     * 할 일 완료 토글
     */
    @Transactional
    public TodoDto toggleComplete(String userId, Long todoId) {
        Todo entity = todoRepository.findById(todoId)
                .filter(t -> t.getUserId().equals(userId))
                .orElseThrow(() -> new IllegalArgumentException("Todo not found: " + todoId));

        entity.setCompleted(!entity.getCompleted());
        return TodoDto.from(todoRepository.save(entity));
    }

    /**
     * 할 일 삭제
     */
    @Transactional
    public void deleteTodo(String userId, Long todoId) {
        todoRepository.findById(todoId)
                .filter(t -> t.getUserId().equals(userId))
                .ifPresent(todoRepository::delete);
    }

    /**
     * 완료된 할 일 모두 삭제
     */
    @Transactional
    public void clearCompleted(String userId) {
        List<Todo> completed = todoRepository.findByUserIdAndCompletedOrderByPriorityDescCreatedAtDesc(userId, true);
        todoRepository.deleteAll(completed);
        log.info("Cleared {} completed todos for user {}", completed.size(), userId);
    }
}
