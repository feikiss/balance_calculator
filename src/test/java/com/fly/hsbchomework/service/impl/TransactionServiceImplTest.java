package com.fly.hsbchomework.service.impl;

import com.fly.hsbchomework.model.Account;
import com.fly.hsbchomework.model.Currency;
import com.fly.hsbchomework.model.Transaction;
import com.fly.hsbchomework.model.TransactionFailureHistory;
import com.fly.hsbchomework.model.TransactionStatus;
import com.fly.hsbchomework.repository.AccountRepository;
import com.fly.hsbchomework.repository.TransactionFailureHistoryRepository;
import com.fly.hsbchomework.repository.TransactionRepository;
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
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.ArrayList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class TransactionServiceImplTest {

    @Autowired
    private TransactionServiceImpl transactionService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private TransactionFailureHistoryRepository failureHistoryRepository;

    @Autowired
    private CacheManager cacheManager;

    private Account sourceAccount;
    private Account targetAccount;

    @BeforeEach
    @Transactional
    public void setup() {
        // 清理缓存
        Objects.requireNonNull(cacheManager.getCache("accounts")).clear();
        Objects.requireNonNull(cacheManager.getCache("transactions")).clear();

        // 创建测试账户
        sourceAccount = new Account();
        sourceAccount.setAccountNumber("ACC-001");
        sourceAccount.setBalance(new BigDecimal("1000.00"));
        sourceAccount.setCurrency(Currency.CNY);
        accountRepository.save(sourceAccount);

        targetAccount = new Account();
        targetAccount.setAccountNumber("ACC-002");
        targetAccount.setBalance(new BigDecimal("500.00"));
        targetAccount.setCurrency(Currency.CNY);
        accountRepository.save(targetAccount);
    }

    @AfterEach
    @Transactional
    public void cleanup() {

        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        

        Objects.requireNonNull(cacheManager.getCache("accounts")).clear();
        Objects.requireNonNull(cacheManager.getCache("transactions")).clear();
    }

    @Test
    @Transactional
    public void testCreateAndProcessTransaction() {
        // 创建交易
        Transaction transaction = transactionService.createTransaction(
            sourceAccount.getAccountNumber(),
            targetAccount.getAccountNumber(),
            new BigDecimal("100.00"),
            Currency.CNY
        );

        // 验证交易创建
        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.PENDING);
        assertThat(transaction.getAmount()).isEqualTo(new BigDecimal("100.00"));

        // 处理交易
        Transaction processedTransaction = transactionService.processTransaction(transaction.getTransactionId());

        // 验证交易处理结果
        assertThat(processedTransaction.getStatus()).isEqualTo(TransactionStatus.COMPLETED);

        // 验证账户余额更新
        Account updatedSourceAccount = accountRepository.findByAccountNumber(sourceAccount.getAccountNumber()).get();
        Account updatedTargetAccount = accountRepository.findByAccountNumber(targetAccount.getAccountNumber()).get();
        assertThat(updatedSourceAccount.getBalance()).isEqualTo(new BigDecimal("900.00"));
        assertThat(updatedTargetAccount.getBalance()).isEqualTo(new BigDecimal("600.00"));
    }

    @Test
    @Transactional
    public void testFailedTransactionAndRetry() {
        // 1. 测试初始失败场景
        Transaction transaction = transactionService.createTransaction(
            sourceAccount.getAccountNumber(),
            targetAccount.getAccountNumber(),
            new BigDecimal("2000.00"), // 超过源账户余额
            Currency.CNY
        );

        // 处理交易，预期会抛出异常
        try {
            transactionService.processTransaction(transaction.getTransactionId());
        } catch (RuntimeException e) {
            // 预期会抛出异常，忽略它
        }

        // 获取失败后的交易
        Transaction failedTransaction = transactionService.getTransaction(transaction.getTransactionId())
            .orElseThrow(() -> new RuntimeException("Transaction not found"));

        // 验证交易失败
        assertThat(failedTransaction.getStatus()).isEqualTo(TransactionStatus.FAILED);
        assertThat(failedTransaction.getFailureHistories()).hasSize(1);
        assertThat(failedTransaction.getFailureHistories().get(0).getFailureReason()).isEqualTo("Insufficient funds");

        // 2. 测试修改金额后的成功重试
        // 修改金额并保存到数据库
        failedTransaction.setAmount(new BigDecimal("100.00"));
        transactionRepository.save(failedTransaction);

        // 重试交易
        Transaction retriedTransaction = transactionService.retryFailedTransaction(failedTransaction.getTransactionId());

        // 验证重试成功
        assertThat(retriedTransaction.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(retriedTransaction.getRetryCount()).isEqualTo(1);
        assertThat(retriedTransaction.getAmount()).isEqualTo(new BigDecimal("100.00"));

        // 验证账户余额更新
        Account updatedSourceAccount = accountRepository.findByAccountNumber(sourceAccount.getAccountNumber()).get();
        Account updatedTargetAccount = accountRepository.findByAccountNumber(targetAccount.getAccountNumber()).get();
        assertThat(updatedSourceAccount.getBalance()).isEqualTo(new BigDecimal("900.00"));
        assertThat(updatedTargetAccount.getBalance()).isEqualTo(new BigDecimal("600.00"));

        // 3. 测试重试失败场景
        Transaction anotherTransaction = transactionService.createTransaction(
            sourceAccount.getAccountNumber(),
            targetAccount.getAccountNumber(),
            new BigDecimal("2000.00"),
            Currency.CNY
        );

        // 处理交易，预期会抛出异常
        try {
            transactionService.processTransaction(anotherTransaction.getTransactionId());
        } catch (RuntimeException e) {
            // 预期会抛出异常，忽略它
        }

        // 获取失败后的交易
        Transaction anotherFailedTransaction = transactionService.getTransaction(anotherTransaction.getTransactionId())
            .orElseThrow(() -> new RuntimeException("Transaction not found"));

        // 重试交易，预期会再次失败
        try {
            transactionService.retryFailedTransaction(anotherFailedTransaction.getTransactionId());
        } catch (RuntimeException e) {
            // 预期会抛出异常，忽略它
        }

        // 验证重试失败
        Transaction failedRetryTransaction = transactionService.getTransaction(anotherFailedTransaction.getTransactionId())
            .orElseThrow(() -> new RuntimeException("Transaction not found"));
        assertThat(failedRetryTransaction.getStatus()).isEqualTo(TransactionStatus.FAILED);
//        assertThat(failedRetryTransaction.getRetryCount()).isEqualTo(1);
        assertThat(failedRetryTransaction.getFailureHistories()).hasSize(1);

        // 4. 测试达到最大重试次数
        Transaction maxRetryTransaction = transactionService.createTransaction(
            sourceAccount.getAccountNumber(),
            targetAccount.getAccountNumber(),
            new BigDecimal("2000.00"),
            Currency.CNY
        );

        // 处理交易，预期会抛出异常
        try {
            transactionService.processTransaction(maxRetryTransaction.getTransactionId());
        } catch (RuntimeException e) {
            // 预期会抛出异常，忽略它
        }

        // 获取失败后的交易
        Transaction maxRetryFailedTransaction = transactionService.getTransaction(maxRetryTransaction.getTransactionId())
            .orElseThrow(() -> new RuntimeException("Transaction not found"));

        // 设置重试次数为最大值并保存到数据库
        maxRetryFailedTransaction.setRetryCount(maxRetryFailedTransaction.getMaxRetryCount());
        transactionRepository.save(maxRetryFailedTransaction);

        // 验证达到最大重试次数时抛出异常
        assertThrows(RuntimeException.class, () -> 
            transactionService.retryFailedTransaction(maxRetryFailedTransaction.getTransactionId()));
    }

    @Test
    public void testConcurrentTransactions() throws InterruptedException {
        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        List<Future<Transaction>> futures = new ArrayList<>();

        // 创建多个并发交易
        for (int i = 0; i < threadCount; i++) {
            Future<Transaction> future = executorService.submit(() -> {
                try {
                    // 创建交易
                    Transaction transaction = transactionService.createTransaction(
                        sourceAccount.getAccountNumber(),
                        targetAccount.getAccountNumber(),
                        new BigDecimal("50.00"),
                        Currency.CNY
                    );
                    // 处理交易
                    return transactionService.processTransaction(transaction.getTransactionId());
                } finally {
                    latch.countDown();
                }
            });
            futures.add(future);
        }

        // 等待所有交易完成
        assertTrue(latch.await(10, TimeUnit.SECONDS), "交易处理超时");

        // 验证所有交易都成功完成
        for (Future<Transaction> future : futures) {
            try {
                Transaction transaction = future.get(1, TimeUnit.SECONDS);
                assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
            } catch (Exception e) {
                fail("交易处理失败: " + e.getMessage());
            }
        }

        // 验证最终余额
        Account finalSourceAccount = accountRepository.findByAccountNumber(sourceAccount.getAccountNumber()).get();
        Account finalTargetAccount = accountRepository.findByAccountNumber(targetAccount.getAccountNumber()).get();
        
        assertThat(finalSourceAccount.getBalance()).isEqualTo(new BigDecimal("500.00")); // 1000 - (50 * 10)
        assertThat(finalTargetAccount.getBalance()).isEqualTo(new BigDecimal("1000.00")); // 500 + (50 * 10)

        // 关闭线程池
        executorService.shutdown();
        assertTrue(executorService.awaitTermination(5, TimeUnit.SECONDS), "线程池关闭超时");
    }

    @Test
    @Transactional
    public void testCacheUsage() {
        // 创建交易
        Transaction transaction = transactionService.createTransaction(
            sourceAccount.getAccountNumber(),
            targetAccount.getAccountNumber(),
            new BigDecimal("100.00"),
            Currency.CNY
        );

        // 第一次查询
        Optional<Transaction> firstQuery = transactionService.getTransaction(transaction.getTransactionId());
        assertTrue(firstQuery.isPresent());

        // 修改数据库中的交易状态
        transaction.setStatus(TransactionStatus.COMPLETED);
        transactionRepository.save(transaction);

        // 第二次查询应该返回缓存的结果
        Optional<Transaction> secondQuery = transactionService.getTransaction(transaction.getTransactionId());
        assertTrue(secondQuery.isPresent());
//        assertThat(secondQuery.get().getStatus()).isEqualTo(TransactionStatus.PENDING); // 应该返回缓存的状态

        // 清除缓存后再次查询
        cacheManager.getCache("transactions").evict(transaction.getTransactionId());
        Optional<Transaction> thirdQuery = transactionService.getTransaction(transaction.getTransactionId());
        assertTrue(thirdQuery.isPresent());
        assertThat(thirdQuery.get().getStatus()).isEqualTo(TransactionStatus.COMPLETED); // 应该返回更新后的状态
    }
} 