package com.fly.hsbchomework.repository;

import com.fly.hsbchomework.model.TransactionFailureHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface TransactionFailureHistoryRepository extends JpaRepository<TransactionFailureHistory, Long> {
    List<TransactionFailureHistory> findByTransactionIdOrderByFailureTimeDesc(Long t_Id);
} 