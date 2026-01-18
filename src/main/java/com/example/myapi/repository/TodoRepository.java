package com.example.myapi.repository;

import com.example.myapi.entity.Todo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TodoRepository extends JpaRepository<Todo, Long> {
    List<Todo> findByUserIdOrderByPriorityDescCreatedAtDesc(String userId);
    List<Todo> findByUserIdAndCompletedOrderByPriorityDescCreatedAtDesc(String userId, Boolean completed);
    long countByUserIdAndCompletedFalse(String userId);
}
