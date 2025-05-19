package com.fly.hsbchomework.controller;

import com.fly.hsbchomework.model.Transaction;
import com.fly.hsbchomework.model.Currency;
import com.fly.hsbchomework.service.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping
    public ResponseEntity<Transaction> createTransaction(@RequestParam String sourceAccountNumber,
                                                        @RequestParam String targetAccountNumber,
                                                        @RequestParam BigDecimal amount,
                                                        @RequestParam Currency currency) {
        log.info("Received request to create transaction: sourceAccount={}, targetAccount={}, amount={}, currency={}",
                sourceAccountNumber, targetAccountNumber, amount, currency);
        
        Transaction created = transactionService.createTransaction(
                sourceAccountNumber, targetAccountNumber, amount, currency
        );
        
        log.info("Transaction created successfully: transactionId={}", created.getTransactionId());
        return ResponseEntity.ok(created);
    }

    @PostMapping("/{transactionId}/process")
    public ResponseEntity<Transaction> processTransaction(@PathVariable String transactionId) {
        log.info("Received request to process transaction: transactionId={}", transactionId);
        
        Transaction processed = transactionService.processTransaction(transactionId);
        
        log.info("Transaction processed successfully: transactionId={}, status={}", 
                processed.getTransactionId(), processed.getStatus());
        return ResponseEntity.ok(processed);
    }

    @GetMapping("/{transactionId}")
    public ResponseEntity<Transaction> getTransaction(@PathVariable String transactionId) {
        log.info("Received request to get transaction: transactionId={}", transactionId);
        
        Optional<Transaction> transaction = transactionService.getTransaction(transactionId);
        
        if (transaction.isPresent()) {
            log.info("Transaction retrieved successfully: transactionId={}, status={}", 
                    transaction.get().getTransactionId(), transaction.get().getStatus());
            return ResponseEntity.ok(transaction.get());
        } else {
            log.info("Transaction not found: transactionId={}", transactionId);
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{transactionId}/retry")
    public ResponseEntity<Transaction> retryFailedTransaction(@PathVariable String transactionId) {
        return ResponseEntity.ok(transactionService.retryFailedTransaction(transactionId));
    }

    @GetMapping
    public ResponseEntity<List<Transaction>> getAllTransactions() {
        log.info("Received request to get all transactions");
        
        List<Transaction> transactions = transactionService.getAllTransactions();
        
        log.info("Retrieved {} transactions successfully", transactions.size());
        return ResponseEntity.ok(transactions);
    }

    @GetMapping("/page")
    public ResponseEntity<Page<Transaction>> getTransactionsByPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {
        log.info("Received request to get transactions by page: page={}, size={}, sortBy={}, direction={}",
                page, size, sortBy, direction);
        
        Sort.Direction sortDirection = Sort.Direction.fromString(direction.toUpperCase());
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(sortDirection, sortBy));
        
        Page<Transaction> transactions = transactionService.getTransactionsByPage(pageRequest);
        log.info("Retrieved {} transactions for page {}", transactions.getContent().size(), page);
        
        return ResponseEntity.ok(transactions);
    }

    @GetMapping("/account/{accountNumber}/page")
    public ResponseEntity<Page<Transaction>> getTransactionsByAccountNumber(
            @PathVariable String accountNumber,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {
        log.info("Received request to get transactions for account {} by page: page={}, size={}, sortBy={}, direction={}",
                accountNumber, page, size, sortBy, direction);
        
        Sort.Direction sortDirection = Sort.Direction.fromString(direction.toUpperCase());
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(sortDirection, sortBy));
        
        Page<Transaction> transactions = transactionService.getTransactionsByAccountNumber(accountNumber, pageRequest);
        log.info("Retrieved {} transactions for account {} on page {}", 
                transactions.getContent().size(), accountNumber, page);
        
        return ResponseEntity.ok(transactions);
    }
} 