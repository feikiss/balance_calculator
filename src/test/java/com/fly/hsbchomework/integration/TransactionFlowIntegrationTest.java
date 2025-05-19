package com.fly.hsbchomework.integration;

import com.fly.hsbchomework.model.Account;
import com.fly.hsbchomework.model.Currency;
import com.fly.hsbchomework.model.Transaction;
import com.fly.hsbchomework.model.TransactionStatus;
import com.fly.hsbchomework.repository.AccountRepository;
import com.fly.hsbchomework.repository.TransactionRepository;
import com.fly.hsbchomework.service.AccountService;
import com.fly.hsbchomework.service.TransactionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class TransactionFlowIntegrationTest {

    @Autowired
    private AccountService accountService;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private CacheManager cacheManager;

    private String sourceAccountNumber;
    private String targetAccountNumber;

    @BeforeEach
    public void setup() {

        sourceAccountNumber = "A20001";
        targetAccountNumber = "A20002";
        accountService.createAccount(sourceAccountNumber, new BigDecimal("1000.00"), Currency.CNY);
        accountService.createAccount(targetAccountNumber, new BigDecimal("500.00"), Currency.CNY);
    }

    @AfterEach
    public void cleanup() {
        // clear
        transactionRepository.deleteAll();
        accountRepository.deleteAll();

        // clear
        Objects.requireNonNull(cacheManager.getCache("accounts")).clear();
        Objects.requireNonNull(cacheManager.getCache("transactions")).clear();
    }

    @Test
    public void testCompleteTransactionFlow() {

        Transaction transaction = transactionService.createTransaction(
            sourceAccountNumber, targetAccountNumber, new BigDecimal("100.00"), Currency.CNY);
        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.PENDING);


        Transaction processedTransaction = transactionService.processTransaction(transaction.getTransactionId());
        assertThat(processedTransaction.getStatus()).isEqualTo(TransactionStatus.COMPLETED);


        Optional<Account> sourceAccount = accountService.getAccount(sourceAccountNumber);
        Optional<Account> targetAccount = accountService.getAccount(targetAccountNumber);
        
        assertThat(sourceAccount).isPresent();
        assertThat(targetAccount).isPresent();
        assertThat(sourceAccount.get().getBalance()).isEqualTo(new BigDecimal("900.00"));
        assertThat(targetAccount.get().getBalance()).isEqualTo(new BigDecimal("600.00"));
    }

    @Test
    public void testFailedTransactionFlow() {

        Transaction transaction = transactionService.createTransaction(
            sourceAccountNumber, targetAccountNumber, new BigDecimal("2000.00"), Currency.CNY);
        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.PENDING);


        assertThrows(RuntimeException.class, () -> 
            transactionService.processTransaction(transaction.getTransactionId()));


        Optional<Account> sourceAccount = accountService.getAccount(sourceAccountNumber);
        Optional<Account> targetAccount = accountService.getAccount(targetAccountNumber);
        
        assertThat(sourceAccount).isPresent();
        assertThat(targetAccount).isPresent();
        assertThat(sourceAccount.get().getBalance()).isEqualTo(new BigDecimal("1000.00"));
        assertThat(targetAccount.get().getBalance()).isEqualTo(new BigDecimal("500.00"));
    }

    @Test
    public void testRetryFailedTransaction() {

        Transaction transaction = transactionService.createTransaction(
            sourceAccountNumber, targetAccountNumber, new BigDecimal("2000.00"), Currency.CNY);
        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.PENDING);


        assertThrows(RuntimeException.class, () -> 
            transactionService.processTransaction(transaction.getTransactionId()));


        try{
            transactionService.retryFailedTransaction(transaction.getTransactionId());
        }catch (Exception ignore){

        }


        Optional<Transaction> retriedTransaction = transactionService.getTransaction(transaction.getTransactionId());
        assertThat(retriedTransaction).isPresent();
        assertThat(retriedTransaction.get().getStatus()).isEqualTo(TransactionStatus.FAILED);
    }
} 