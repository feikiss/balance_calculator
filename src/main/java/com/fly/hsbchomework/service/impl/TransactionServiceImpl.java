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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
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

    private final ReentrantLock accountLock = new ReentrantLock();

    @Override
    @Transactional
    @CacheEvict(value = {"transactions"}, allEntries = true)
    public Transaction createTransaction(String sourceAccountNumber, String targetAccountNumber,
                                      BigDecimal amount, Currency currency) {
        log.info("Creating new transaction: sourceAccount={}, targetAccount={}, amount={}, currency={}",
                sourceAccountNumber, targetAccountNumber, amount, currency);

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
    }

    @Override
    @Transactional
    @CacheEvict(value = {"transactions"}, key = "#transactionId")
    public Transaction processTransaction(String transactionId) {
        log.info("Processing transaction: transactionId={}", transactionId);

        Transaction transaction = transactionRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> {
                    log.error("Transaction not found: transactionId={}", transactionId);
                    return new EntityNotFoundException("Transaction not found");
                });

        if (transaction.getStatus() != TransactionStatus.PENDING) {
            log.warn("Transaction is not in PENDING status: transactionId={}, status={}",
                    transactionId, transaction.getStatus());
            throw new IllegalStateException("Transaction is not in PENDING status");
        }

        try {
            accountLock.lock();

            Account sourceAccount = accountRepository.findByAccountNumberWithLock(transaction.getSourceAccountNumber())
                    .orElseThrow(() -> {
                        log.error("Source account not found: accountNumber={}", transaction.getSourceAccountNumber());
                        return new EntityNotFoundException("Source account not found");
                    });
            Account targetAccount = accountRepository.findByAccountNumberWithLock(transaction.getTargetAccountNumber())
                    .orElseThrow(() -> {
                        log.error("Target account not found: accountNumber={}", transaction.getTargetAccountNumber());
                        return new EntityNotFoundException("Target account not found");
                    });


            if (sourceAccount.getBalance().compareTo(transaction.getAmount()) < 0) {
                throw new RuntimeException("Insufficient funds");
            }

            // update
            sourceAccount.setBalance(sourceAccount.getBalance().subtract(transaction.getAmount()));
            targetAccount.setBalance(targetAccount.getBalance().add(transaction.getAmount()));


            accountRepository.save(sourceAccount);
            accountRepository.save(targetAccount);

            // update status
            transaction.setStatus(TransactionStatus.COMPLETED);
            Transaction processed = transactionRepository.save(transaction);

            log.info("Transaction processed successfully: transactionId={}, status={}",
                    processed.getTransactionId(), processed.getStatus());
            return processed;
        } catch (Exception e) {
            log.error("Error processing transaction: transactionId={}, error={}", transactionId, e.getMessage());
            transaction.setStatus(TransactionStatus.FAILED);
            transaction.setErrorMessage(e.getMessage());
            Transaction failed = transactionRepository.save(transaction);
            recordFailure(failed, e.getMessage());
            throw new RuntimeException("Failed to process transaction", e);
        } finally {
            accountLock.unlock();
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
            accountLock.lock();
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
            accountLock.unlock();
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