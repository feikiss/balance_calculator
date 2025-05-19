package com.fly.hsbchomework.integration;

import com.fly.hsbchomework.model.Account;
import com.fly.hsbchomework.model.Currency;
import com.fly.hsbchomework.model.Transaction;
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
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class AccountTransactionIntegrationTest {

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

    private Account sourceAccount;
    private Account targetAccount;

    @BeforeEach
    public void setup() {
        // accuont
        sourceAccount = accountService.createAccount("1234567890", new BigDecimal("1000.00"), Currency.CNY);
        targetAccount = accountService.createAccount("0987654321", new BigDecimal("500.00"), Currency.CNY);
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
            sourceAccount.getAccountNumber(),
            targetAccount.getAccountNumber(),
            new BigDecimal("100.00"),
            Currency.CNY
        );
        assertNotNull(transaction);
        assertEquals("PENDING", transaction.getStatus().name());


        Transaction processedTransaction = transactionService.processTransaction(transaction.getTransactionId());
        assertNotNull(processedTransaction);
        assertEquals("COMPLETED", processedTransaction.getStatus().name());


        Optional<Account> updatedSourceAccount = accountRepository.findById(sourceAccount.getId());
        Optional<Account> updatedTargetAccount = accountRepository.findById(targetAccount.getId());

        assertTrue(updatedSourceAccount.isPresent());
        assertTrue(updatedTargetAccount.isPresent());

        assertEquals(new BigDecimal("900.00"), updatedSourceAccount.get().getBalance());
        assertEquals(new BigDecimal("600.00"), updatedTargetAccount.get().getBalance());
    }

    @Test
    public void testFailedTransactionFlow() {
        // create order
        Transaction transaction = transactionService.createTransaction(
            sourceAccount.getAccountNumber(),
            targetAccount.getAccountNumber(),
            new BigDecimal("2000.00"),
            Currency.CNY
        );
        assertNotNull(transaction);
        assertEquals("PENDING", transaction.getStatus().name());


        try{
            transactionService.processTransaction(transaction.getTransactionId());
        }catch (Exception ignored){

        }

        Optional<Account> updatedSourceAccount = accountRepository.findById(sourceAccount.getId());
        Optional<Account> updatedTargetAccount = accountRepository.findById(targetAccount.getId());

        assertTrue(updatedSourceAccount.isPresent());
        assertTrue(updatedTargetAccount.isPresent());

        assertEquals(new BigDecimal("1000.00"), updatedSourceAccount.get().getBalance());
        assertEquals(new BigDecimal("500.00"), updatedTargetAccount.get().getBalance());
    }

} 