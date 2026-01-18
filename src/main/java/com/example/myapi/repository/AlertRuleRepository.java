package com.example.myapi.repository;

import com.example.myapi.entity.AlertRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlertRuleRepository extends JpaRepository<AlertRule, Long> {
    List<AlertRule> findByUserId(String userId);
    List<AlertRule> findByUserIdAndEnabled(String userId, Boolean enabled);
    List<AlertRule> findByUserIdAndType(String userId, String type);
    List<AlertRule> findByEnabledTrue();
}
