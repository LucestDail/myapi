package com.example.myapi.controller;

import com.example.myapi.dto.productivity.TodoDto;
import com.example.myapi.service.TodoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 할 일 API 컨트롤러
 */
@RestController
@RequestMapping("/api/todos")
@CrossOrigin(origins = "*")
public class TodoController {

    private final TodoService todoService;

    public TodoController(TodoService todoService) {
        this.todoService = todoService;
    }

    /**
     * 할 일 목록 조회
     */
    @GetMapping
    public ResponseEntity<List<TodoDto>> getTodos(
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @RequestParam(required = false) String filter) {
        String effectiveUserId = userId != null ? userId : headerUserId;
        if (effectiveUserId == null) {
            return ResponseEntity.badRequest().build();
        }

        List<TodoDto> todos;
        if (filter == null) {
            todos = todoService.getTodos(effectiveUserId);
        } else {
            todos = switch (filter) {
                case "pending" -> todoService.getPendingTodos(effectiveUserId);
                case "completed" -> todoService.getCompletedTodos(effectiveUserId);
                default -> todoService.getTodos(effectiveUserId);
            };
        }

        return ResponseEntity.ok(todos);
    }

    /**
     * 미완료 개수 조회
     */
    @GetMapping("/count")
    public ResponseEntity<Map<String, Long>> getPendingCount(
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId) {
        String effectiveUserId = userId != null ? userId : headerUserId;
        if (effectiveUserId == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(Map.of("pending", todoService.getPendingCount(effectiveUserId)));
    }

    /**
     * 할 일 생성
     */
    @PostMapping
    public ResponseEntity<TodoDto> createTodo(
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @RequestBody TodoDto dto) {
        String effectiveUserId = userId != null ? userId : headerUserId;
        if (effectiveUserId == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(todoService.createTodo(effectiveUserId, dto));
    }

    /**
     * 할 일 수정
     */
    @PutMapping("/{todoId}")
    public ResponseEntity<TodoDto> updateTodo(
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @PathVariable Long todoId,
            @RequestBody TodoDto dto) {
        String effectiveUserId = userId != null ? userId : headerUserId;
        if (effectiveUserId == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(todoService.updateTodo(effectiveUserId, todoId, dto));
    }

    /**
     * 할 일 완료 토글
     */
    @PatchMapping("/{todoId}/toggle")
    public ResponseEntity<TodoDto> toggleComplete(
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @PathVariable Long todoId) {
        String effectiveUserId = userId != null ? userId : headerUserId;
        if (effectiveUserId == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(todoService.toggleComplete(effectiveUserId, todoId));
    }

    /**
     * 할 일 삭제
     */
    @DeleteMapping("/{todoId}")
    public ResponseEntity<Void> deleteTodo(
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @PathVariable Long todoId) {
        String effectiveUserId = userId != null ? userId : headerUserId;
        if (effectiveUserId == null) {
            return ResponseEntity.badRequest().build();
        }
        todoService.deleteTodo(effectiveUserId, todoId);
        return ResponseEntity.ok().build();
    }

    /**
     * 완료된 할 일 모두 삭제
     */
    @DeleteMapping("/completed")
    public ResponseEntity<Void> clearCompleted(
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId) {
        String effectiveUserId = userId != null ? userId : headerUserId;
        if (effectiveUserId == null) {
            return ResponseEntity.badRequest().build();
        }
        todoService.clearCompleted(effectiveUserId);
        return ResponseEntity.ok().build();
    }
}
