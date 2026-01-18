package com.example.myapi.dto.productivity;

import com.example.myapi.entity.Todo;

import java.time.Instant;

/**
 * 할 일 DTO
 */
public record TodoDto(
        Long id,
        String content,
        Boolean completed,
        Integer priority,
        Instant dueDate,
        Instant createdAt,
        Instant updatedAt
) {
    public static TodoDto from(Todo entity) {
        return new TodoDto(
                entity.getId(),
                entity.getContent(),
                entity.getCompleted(),
                entity.getPriority(),
                entity.getDueDate(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public Todo toEntity(String userId) {
        Todo entity = new Todo(userId, content);
        entity.setCompleted(completed != null ? completed : false);
        entity.setPriority(priority != null ? priority : 0);
        entity.setDueDate(dueDate);
        return entity;
    }
}
