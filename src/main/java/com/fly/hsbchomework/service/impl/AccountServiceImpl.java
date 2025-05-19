package com.fly.hsbchomework.service.impl;

import com.fly.hsbchomework.model.Account;
import com.fly.hsbchomework.model.Currency;
import com.fly.hsbchomework.repository.AccountRepository;
import com.fly.hsbchomework.service.AccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {

    private final AccountRepository accountRepository;

    @Override
    @Transactional
    public Account createAccount(String accountNumber, BigDecimal initialBalance, Currency currency) {
        log.info("Creating new account: accountNumber={}, initialBalance={}, currency={}",
                accountNumber, initialBalance, currency);

        if (accountRepository.findByAccountNumber(accountNumber).isPresent()) {
            log.error("Account already exists: accountNumber={}", accountNumber);
            throw new IllegalStateException("Account already exists");
        }

        Account account = new Account();
        account.setAccountNumber(accountNumber);
        account.setBalance(initialBalance);
        account.setCurrency(currency);

        Account saved = accountRepository.save(account);
        log.info("Account created successfully: accountNumber={}, id={}", 
                saved.getAccountNumber(), saved.getId());
        return saved;
    }

    @Override
    @Cacheable(value = "accounts", key = "#accountNumber")
    public Optional<Account> getAccount(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber);
    }

    @Override
    public List<Account> getAllAccounts() {
        return null;
    }

    @Override
    @Transactional
    @CacheEvict(value = "accounts", key = "#accountNumber")
    public Account updateBalance(String accountNumber, BigDecimal amount) {
        log.info("Updating account balance: accountNumber={}, amount={}", accountNumber, amount);

        Account account = accountRepository.findByAccountNumberWithLock(accountNumber)
                .orElseThrow(() -> {
                    log.error("Account not found: accountNumber={}", accountNumber);
                    return new RuntimeException("Account not found: " + accountNumber);
                });
        
        BigDecimal newBalance = account.getBalance().add(amount);
        if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
            log.error("Insufficient funds for account: accountNumber={}, currentBalance={}, requestedAmount={}", 
                    accountNumber, account.getBalance(), amount);
            throw new RuntimeException("Insufficient funds for account: " + accountNumber);
        }

        account.setBalance(newBalance);
        Account updated = accountRepository.save(account);
        log.info("Account balance updated successfully: accountNumber={}, newBalance={}", 
                updated.getAccountNumber(), updated.getBalance());
        return updated;
    }

    @Override
    @Transactional
    public void deleteAccount(String accountNumber) {
        log.info("Deleting account: accountNumber={}", accountNumber);

        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> {
                    log.error("Account not found: accountNumber={}", accountNumber);
                    return new EntityNotFoundException("Account not found");
                });


        if (account.getBalance().compareTo(BigDecimal.ZERO) > 0) {
            log.error("Cannot delete account with non-zero balance: accountNumber={}, balance={}", 
                    accountNumber, account.getBalance());
            throw new IllegalStateException("Cannot delete account with non-zero balance");
        }

        accountRepository.delete(account);
        log.info("Account deleted successfully: accountNumber={}", accountNumber);
    }
} 