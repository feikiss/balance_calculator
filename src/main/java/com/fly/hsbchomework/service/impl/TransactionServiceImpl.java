package com.fly.hsbchomework.service.impl;

import com.fly.hsbchomework.model.Account;
import com.fly.hsbchomework.model.Transaction;
import com.fly.hsbchomework.model.TransactionFailureHistory;
import com.fly.hsbchomework.model.TransactionStatus;
import com.fly.hsbchomework.model.Currency;
import com.fly.hsbchomework.repository.AccountRepository;
import com.fly.hsbchomework.repository.TransactionFailureHistoryRepository;
import com.fly.hsbchomework.repository.TransactionRepository;
import com.fly.hsbchomework.service.TransactionService;
import com.fly.hsbchomework.service.AccountService;
import com.fly.hsbchomework.util.RedisDistributedLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.persistence.EntityNotFoundException;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final TransactionFailureHistoryRepository failureHistoryRepository;
    private final AccountService accountService;
    private final RedisDistributedLock distributedLock;

    private static final String TRANSACTION_LOCK_PREFIX = "transaction:lock:";
    private static final String ACCOUNT_LOCK_PREFIX = "account:lock:";
    private static final long LOCK_TIMEOUT = 30;
    private static final TimeUnit LOCK_TIME_UNIT = TimeUnit.SECONDS;

    @Override
    @Transactional
    @CacheEvict(value = {"transactions"}, allEntries = true)
    public Transaction createTransaction(String sourceAccountNumber, String targetAccountNumber,
                                      BigDecimal amount, Currency currency) {
        log.info("Creating new transaction: sourceAccount={}, targetAccount={}, amount={}, currency={}",
                sourceAccountNumber, targetAccountNumber, amount, currency);

        final String requestId = UUID.randomUUID().toString();
        final String sourceLockKey = ACCOUNT_LOCK_PREFIX + sourceAccountNumber;
        final String targetLockKey = ACCOUNT_LOCK_PREFIX + targetAccountNumber;

        try {
            // Try to acquire locks for both accounts
            if (!distributedLock.tryLock(sourceLockKey, requestId, LOCK_TIMEOUT, LOCK_TIME_UNIT)) {
                throw new RuntimeException("Failed to acquire lock for source account");
            }
            if (!distributedLock.tryLock(targetLockKey, requestId, LOCK_TIMEOUT, LOCK_TIME_UNIT)) {
                distributedLock.releaseLock(sourceLockKey, requestId);
                throw new RuntimeException("Failed to acquire lock for target account");
            }

            Account sourceAccount = accountRepository.findByAccountNumber(sourceAccountNumber)
                    .orElseThrow(() -> {
                        log.error("Source account not found: accountNumber={}", sourceAccountNumber);
                        return new EntityNotFoundException("Source account not found");
                    });

            Account targetAccount = accountRepository.findByAccountNumber(targetAccountNumber)
                    .orElseThrow(() -> {
                        log.error("Target account not found: accountNumber={}", targetAccountNumber);
                        return new EntityNotFoundException("Target account not found");
                    });

            Transaction transaction = new Transaction();
            transaction.setTransactionId(UUID.randomUUID().toString());
            transaction.setSourceAccountNumber(sourceAccountNumber);
            transaction.setTargetAccountNumber(targetAccountNumber);
            transaction.setAmount(amount);
            transaction.setCurrency(currency);
            transaction.setStatus(TransactionStatus.PENDING);

            Transaction saved = transactionRepository.save(transaction);
            log.info("Transaction created successfully: transactionId={}", saved.getTransactionId());
            return saved;
        } finally {
            distributedLock.releaseLock(sourceLockKey, requestId);
            distributedLock.releaseLock(targetLockKey, requestId);
        }
    }

    @Override
    @Transactional
    @Retryable(
        value = {RuntimeException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 100, multiplier = 2)
    )
    @CacheEvict(value = {"transactions"}, key = "#transactionId")
    public Transaction processTransaction(String transactionId) {
        log.info("Processing transaction: transactionId={}", transactionId);

        final String requestId = UUID.randomUUID().toString();
        final String transactionLockKey = TRANSACTION_LOCK_PREFIX + transactionId;

        try {
            // 获取交易锁
            if (!distributedLock.tryLock(transactionLockKey, requestId, LOCK_TIMEOUT, LOCK_TIME_UNIT)) {
                throw new RuntimeException("Failed to acquire transaction lock");
            }

            // 获取并验证交易
            final Transaction transaction = transactionRepository.findByTransactionId(transactionId)
                    .orElseThrow(() -> {
                        log.error("Transaction not found: transactionId={}", transactionId);
                        return new EntityNotFoundException("Transaction not found");
                    });

            // 验证交易状态
            if (transaction.getStatus() != TransactionStatus.PENDING) {
                log.warn("Transaction is not in PENDING status: transactionId={}, status={}",
                        transactionId, transaction.getStatus());
                throw new IllegalStateException("Transaction is not in PENDING status");
            }

            // 按照账户编号排序获取锁，避免死锁
            final String sourceAccountNumber = transaction.getSourceAccountNumber();
            final String targetAccountNumber = transaction.getTargetAccountNumber();
            final String firstLockKey = ACCOUNT_LOCK_PREFIX + 
                    (sourceAccountNumber.compareTo(targetAccountNumber) < 0 ? sourceAccountNumber : targetAccountNumber);
            final String secondLockKey = ACCOUNT_LOCK_PREFIX + 
                    (sourceAccountNumber.compareTo(targetAccountNumber) < 0 ? targetAccountNumber : sourceAccountNumber);

            try {
                // 获取第一个账户锁
                if (!distributedLock.tryLock(firstLockKey, requestId, LOCK_TIMEOUT, LOCK_TIME_UNIT)) {
                    throw new RuntimeException("Failed to acquire first account lock");
                }

                // 获取第二个账户锁
                if (!distributedLock.tryLock(secondLockKey, requestId, LOCK_TIMEOUT, LOCK_TIME_UNIT)) {
                    distributedLock.releaseLock(firstLockKey, requestId);
                    throw new RuntimeException("Failed to acquire second account lock");
                }

                // 获取账户信息
                Account sourceAccount = accountRepository.findByAccountNumberWithLock(sourceAccountNumber)
                        .orElseThrow(() -> {
                            log.error("Source account not found: accountNumber={}", sourceAccountNumber);
                            return new EntityNotFoundException("Source account not found");
                        });
                Account targetAccount = accountRepository.findByAccountNumberWithLock(targetAccountNumber)
                        .orElseThrow(() -> {
                            log.error("Target account not found: accountNumber={}", targetAccountNumber);
                            return new EntityNotFoundException("Target account not found");
                        });

                // 验证账户余额
                if (sourceAccount.getBalance().compareTo(transaction.getAmount()) < 0) {
                    throw new RuntimeException("Insufficient funds in source account");
                }

                // 执行转账
                sourceAccount.setBalance(sourceAccount.getBalance().subtract(transaction.getAmount()));
                targetAccount.setBalance(targetAccount.getBalance().add(transaction.getAmount()));

                // 保存账户更新
                accountRepository.save(sourceAccount);
                accountRepository.save(targetAccount);

                // 更新交易状态
                transaction.setStatus(TransactionStatus.COMPLETED);
                transaction.setProcessedTime(LocalDateTime.now());
                Transaction processed = transactionRepository.save(transaction);

                log.info("Transaction processed successfully: transactionId={}, status={}, amount={}, currency={}",
                        processed.getTransactionId(), processed.getStatus(), processed.getAmount(), processed.getCurrency());
                return processed;

            } finally {
                // 释放账户锁
                distributedLock.releaseLock(firstLockKey, requestId);
                distributedLock.releaseLock(secondLockKey, requestId);
            }

        } catch (Exception e) {
            log.error("Error processing transaction: transactionId={}, error={}", transactionId, e.getMessage());
            Transaction failed = transactionRepository.findByTransactionId(transactionId)
                    .orElseThrow(() -> new EntityNotFoundException("Transaction not found"));
            failed.setStatus(TransactionStatus.FAILED);
            failed.setErrorMessage(e.getMessage());
            failed.setLastRetryTime(LocalDateTime.now());
            failed = transactionRepository.save(failed);
            recordFailure(failed, e.getMessage());
            throw new RuntimeException("Failed to process transaction", e);
        } finally {
            // 释放交易锁
            distributedLock.releaseLock(transactionLockKey, requestId);
        }
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "transactions", key = "#transactionId", unless = "#result == null")
    public Optional<Transaction> getTransaction(String transactionId) {
        log.info("Retrieving transaction from database: transactionId={}", transactionId);
        return transactionRepository.findByTransactionId(transactionId);
    }

    @Override
    @Transactional
    @CacheEvict(value = {"transactions"}, key = "#transactionId")
    public Transaction retryFailedTransaction(String transactionId) {
        Transaction transaction = transactionRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new RuntimeException("Transaction not found: " + transactionId));

        if (transaction.getStatus() != TransactionStatus.FAILED) {
            throw new RuntimeException("Transaction is not in failed status");
        }

        if (transaction.getRetryCount() >= transaction.getMaxRetryCount()) {
            throw new RuntimeException("Maximum retry attempts reached");
        }
        try {
            // 验证账户是否存在
            Account sourceAccount = accountRepository.findByAccountNumber(transaction.getSourceAccountNumber())
                    .orElseThrow(() -> new RuntimeException("Source account not found"));
            Account targetAccount = accountRepository.findByAccountNumber(transaction.getTargetAccountNumber())
                    .orElseThrow(() -> new RuntimeException("Target account not found"));


            validateTransaction(transaction);


            if (sourceAccount.getBalance().compareTo(transaction.getAmount()) < 0) {
                throw new RuntimeException("Insufficient funds in source account");
            }


            transaction.setRetryCount(transaction.getRetryCount() + 1);
            transaction.setLastRetryTime(LocalDateTime.now());
            transaction.setStatus(TransactionStatus.PENDING);


            transaction = transactionRepository.save(transaction);


            // get the account info again.
            sourceAccount = accountRepository.findByAccountNumberWithLock(transaction.getSourceAccountNumber())
                    .orElseThrow(() -> new RuntimeException("Source account not found"));
            targetAccount = accountRepository.findByAccountNumberWithLock(transaction.getTargetAccountNumber())
                    .orElseThrow(() -> new RuntimeException("Target account not found"));

            // check again.
            if (sourceAccount.getBalance().compareTo(transaction.getAmount()) < 0) {
                throw new RuntimeException("Insufficient funds");
            }

            // 更新余额
            sourceAccount.setBalance(sourceAccount.getBalance().subtract(transaction.getAmount()));
            targetAccount.setBalance(targetAccount.getBalance().add(transaction.getAmount()));

            // 保存账户更新
            accountRepository.save(sourceAccount);
            accountRepository.save(targetAccount);

            // 更新交易状态为完成
            transaction.setStatus(TransactionStatus.COMPLETED);
            return transactionRepository.save(transaction);
        } catch (Exception e) {
            transaction.setStatus(TransactionStatus.FAILED);
            transaction.setRetryCount(transaction.getRetryCount()+1);
            transactionRepository.save(transaction);
            recordFailure(transaction, e.getMessage());
            throw e;
        } finally {
           
        }
    }

    private void validateTransaction(Transaction transaction) {
        if (transaction.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("The money must more than 0");
        }
        if (transaction.getSourceAccountNumber().equals(transaction.getTargetAccountNumber())) {
            throw new RuntimeException("The source and target cannot be same.");
        }
    }

    private void recordFailure(Transaction transaction, String reason) {
        TransactionFailureHistory failureHistory = new TransactionFailureHistory();
        failureHistory.setTransaction(transaction);
        failureHistory.setRetryCount(transaction.getRetryCount());
        failureHistory.setFailureTime(LocalDateTime.now());
        failureHistory.setFailureReason(reason);

        transaction.getFailureHistories().add(failureHistory);
        transactionRepository.save(transaction);
    }

    @Override
    public List<Transaction> getAllTransactions() {
        log.info("Retrieving all transactions");
        List<Transaction> transactions = transactionRepository.findAll();
        log.info("Retrieved {} transactions", transactions.size());
        return transactions;
    }

    @Override
    public Page<Transaction> getTransactionsByPage(Pageable pageable) {
        log.info("Fetching transactions with pagination: page={}, size={}, sort={}",
                pageable.getPageNumber(), pageable.getPageSize(), pageable.getSort());
        return transactionRepository.findAll(pageable);
    }

    @Override
    public Page<Transaction> getTransactionsByAccountNumber(String accountNumber, Pageable pageable) {
        log.info("Fetching transactions for account {} with pagination: page={}, size={}, sort={}",
                accountNumber, pageable.getPageNumber(), pageable.getPageSize(), pageable.getSort());
        return transactionRepository.findBySourceAccountNumberOrTargetAccountNumber(
                accountNumber, accountNumber, pageable);
    }
} 