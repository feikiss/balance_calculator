package com.fly.hsbchomework.repository;

import com.fly.hsbchomework.model.Transaction;
import com.fly.hsbchomework.model.TransactionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    Optional<Transaction> findByTransactionId(String transactionId);
    
    boolean existsByTransactionId(String transactionId);
    
    List<Transaction> findByStatus(TransactionStatus status);
    
    List<Transaction> findBySourceAccountNumber(String sourceAccountNumber);
    
    List<Transaction> findByTargetAccountNumber(String targetAccountNumber);
    
    Transaction save(Transaction transaction);

    Page<Transaction> findBySourceAccountNumberOrTargetAccountNumber(
            String sourceAccountNumber, 
            String targetAccountNumber, 
            Pageable pageable);
} 