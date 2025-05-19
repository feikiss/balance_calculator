package com.fly.hsbchomework.service;

import com.fly.hsbchomework.model.Transaction;
import com.fly.hsbchomework.model.Currency;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface TransactionService {
    /**
     * new transaction
     */
    Transaction createTransaction(String sourceAccountNumber, String targetAccountNumber, 
                                BigDecimal amount, Currency currency);

    /**
     * handle transactoin
     */
    Transaction processTransaction(String transactionId);

    /**
     * get details
     */
    Optional<Transaction> getTransaction(String transactionId);

    /**
     * retry the failed transaction
     */
    Transaction retryFailedTransaction(String transactionId);

    List<Transaction> getAllTransactions();

    // query by page
    Page<Transaction> getTransactionsByPage(Pageable pageable);


    Page<Transaction> getTransactionsByAccountNumber(String accountNumber, Pageable pageable);
} 