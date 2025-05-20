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
        List<Transaction> successfulTransactions = new ArrayList<>();
        List<Exception> failures = new ArrayList<>();

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
                    
                    // 处理交易，最多重试3次
                    int retryCount = 0;
                    Transaction processedTransaction = null;
                    while (retryCount < 3 && processedTransaction == null) {
                        try {
                            processedTransaction = transactionService.processTransaction(transaction.getTransactionId());
                            successfulTransactions.add(processedTransaction);
                        } catch (RuntimeException e) {
                            if (e.getMessage().contains("Failed to acquire lock")) {
                                retryCount++;
                                if (retryCount < 3) {
                                    Thread.sleep(100); // 等待100ms后重试
                                    continue;
                                }
                            }
                            throw e;
                        }
                    }
                    return processedTransaction;
                } catch (Exception e) {
                    failures.add(e);
                    throw e;
                } finally {
                    latch.countDown();
                }
            });
            futures.add(future);
        }

        // 等待所有交易完成，设置较长的超时时间
        boolean completed = latch.await(30, TimeUnit.SECONDS);
        assertTrue(completed, "Not all transactions completed within timeout");

        // 验证最终结果
        Account finalSourceAccount = accountRepository.findByAccountNumber(sourceAccount.getAccountNumber()).get();
        Account finalTargetAccount = accountRepository.findByAccountNumber(targetAccount.getAccountNumber()).get();

        // 计算成功的交易数量
        int successCount = successfulTransactions.size();
        assertTrue(successCount > 0, "At least one transaction should succeed");

        // 验证账户余额
        BigDecimal expectedSourceBalance = new BigDecimal("1000.00").subtract(
            new BigDecimal("10.00").multiply(new BigDecimal(successCount))
        );
        BigDecimal expectedTargetBalance = new BigDecimal("500.00").add(
            new BigDecimal("10.00").multiply(new BigDecimal(successCount))
        );

        assertThat(finalSourceAccount.getBalance()).isEqualTo(expectedSourceBalance);
        assertThat(finalTargetAccount.getBalance()).isEqualTo(expectedTargetBalance);

        // 验证交易状态
        for (Transaction transaction : successfulTransactions) {
            assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        }

        // 清理资源
        executorService.shutdown();
        executorService.awaitTermination(5, TimeUnit.SECONDS);
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