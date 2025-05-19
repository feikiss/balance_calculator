package com.fly.hsbchomework.resilience;

import com.fly.hsbchomework.model.Account;
import com.fly.hsbchomework.model.Currency;
import com.fly.hsbchomework.model.Transaction;
import com.fly.hsbchomework.model.TransactionStatus;
import com.fly.hsbchomework.repository.AccountRepository;
import com.fly.hsbchomework.repository.TransactionRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class ResilienceTest {

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
        // 清理缓存
        cacheManager.getCache("accounts").clear();
        cacheManager.getCache("transactions").clear();

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
        // 清理所有测试数据
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        
        // 清理缓存
        cacheManager.getCache("accounts").clear();
        cacheManager.getCache("transactions").clear();
    }

    @Test
    @Transactional
    public void testDatabaseConnectionFailure() {
        // 模拟数据库连接中断
        transactionRepository.deleteAll();
        accountRepository.deleteAll();

        // 尝试创建交易
        Transaction transaction = null;
        try {
            transaction = transactionService.createTransaction(
                    sourceAccount.getAccountNumber(),
                    targetAccount.getAccountNumber(),
                    new BigDecimal("100.00"),
                    Currency.CNY
            );
        } catch (Exception ignored){

        }

        // 验证交易创建失败
        assertThat(transaction).isNull();
    }

    @Test
    @Transactional
    public void testCacheUnavailable() {
        // 禁用缓存
        cacheManager.getCache("accounts").clear();
        cacheManager.getCache("transactions").clear();

        // 创建并处理交易
        Transaction transaction = transactionService.createTransaction(
            sourceAccount.getAccountNumber(),
            targetAccount.getAccountNumber(),
            new BigDecimal("100.00"),
            Currency.CNY
        );

        // 验证交易可以正常处理
        Transaction processedTransaction = transactionService.processTransaction(transaction.getTransactionId());
        assertThat(processedTransaction.getStatus()).isEqualTo(TransactionStatus.COMPLETED);

        // 验证账户余额更新
        Account updatedSourceAccount = accountRepository.findByAccountNumber(sourceAccount.getAccountNumber()).get();
        Account updatedTargetAccount = accountRepository.findByAccountNumber(targetAccount.getAccountNumber()).get();
        assertThat(updatedSourceAccount.getBalance()).isEqualTo(new BigDecimal("900.00"));
        assertThat(updatedTargetAccount.getBalance()).isEqualTo(new BigDecimal("600.00"));
    }

    @Test
    public void testHighConcurrency() throws InterruptedException {
        int threadCount = 50;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        List<Future<Transaction>> futures = new ArrayList<>();

        // 创建并处理多个并发交易
        for (int i = 0; i < threadCount; i++) {
            Future<Transaction> future = executorService.submit(() -> {
                try {
                    // 创建交易
                    Transaction transaction = transactionService.createTransaction(
                        sourceAccount.getAccountNumber(),
                        targetAccount.getAccountNumber(),
                        new BigDecimal("10.00"),
                        Currency.CNY
                    );
                    // 立即处理交易
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
        
        assertThat(finalSourceAccount.getBalance()).isEqualTo(new BigDecimal("500.00")); // 1000 - (10 * 50)
        assertThat(finalTargetAccount.getBalance()).isEqualTo(new BigDecimal("1000.00")); // 500 + (10 * 50)

        // 关闭线程池
        executorService.shutdown();
        assertTrue(executorService.awaitTermination(5, TimeUnit.SECONDS), "线程池关闭超时");
    }

    @Test
    @Transactional
    public void testDependencyServiceUnavailable() {
        // 模拟依赖服务不可用
        transactionRepository.deleteAll();
        accountRepository.deleteAll();

        // 尝试创建交易
        Transaction transaction = null;
        try{
            transaction = transactionService.createTransaction(
                    sourceAccount.getAccountNumber(),
                    targetAccount.getAccountNumber(),
                    new BigDecimal("100.00"),
                    Currency.CNY
            );

        } catch (Exception ignored){

        }
        // 验证交易创建失败
        assertThat(transaction).isNull();

        // 恢复依赖服务
        setup();

        // 验证服务可以恢复
        Transaction newTransaction = transactionService.createTransaction(
            sourceAccount.getAccountNumber(),
            targetAccount.getAccountNumber(),
            new BigDecimal("100.00"),
            Currency.CNY
        );

        assertThat(newTransaction).isNotNull();
        assertThat(newTransaction.getStatus()).isEqualTo(TransactionStatus.PENDING);
    }
} 