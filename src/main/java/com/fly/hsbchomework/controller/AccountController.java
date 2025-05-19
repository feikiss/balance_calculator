package com.fly.hsbchomework.controller;

import com.fly.hsbchomework.model.Account;
import com.fly.hsbchomework.model.Currency;
import com.fly.hsbchomework.service.AccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @PostMapping
    public ResponseEntity<Account> createAccount(
            @RequestParam String accountNumber,
            @RequestParam BigDecimal initialBalance,
            @RequestParam Currency currency) {
        log.info("Received request to create account: accountNumber={}, initialBalance={}, currency={}",
                accountNumber, initialBalance, currency);
        
        Account created = accountService.createAccount(accountNumber, initialBalance, currency);
        
        log.info("Account created successfully: accountNumber={}, id={}", 
                created.getAccountNumber(), created.getId());
        return ResponseEntity.ok(created);
    }

    @GetMapping("/{accountNumber}")
    public ResponseEntity<Account> getAccount(@PathVariable String accountNumber) {
        log.info("Received request to get account: accountNumber={}", accountNumber);
        
        Optional<Account> account = accountService.getAccount(accountNumber);
        
        if (account.isPresent()) {
            log.info("Account retrieved successfully: accountNumber={}, balance={}, currency={}", 
                    account.get().getAccountNumber(), account.get().getBalance(), account.get().getCurrency());
            return ResponseEntity.ok(account.get());
        } else {
            log.info("Account not found: accountNumber={}", accountNumber);
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping
    public ResponseEntity<List<Account>> getAllAccounts() {
        log.info("Received request to get all accounts");
        
        List<Account> accounts = accountService.getAllAccounts();
        
        log.info("Retrieved {} accounts successfully", accounts.size());
        return ResponseEntity.ok(accounts);
    }

    @PutMapping("/{accountNumber}/balance")
    public ResponseEntity<Account> updateBalance(
            @PathVariable String accountNumber,
            @RequestParam BigDecimal amount) {
        log.info("Received request to update account balance: accountNumber={}, amount={}", 
                accountNumber, amount);
        
        Account updated = accountService.updateBalance(accountNumber, amount);
        
        log.info("Account balance updated successfully: accountNumber={}, newBalance={}", 
                updated.getAccountNumber(), updated.getBalance());
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{accountNumber}")
    public ResponseEntity<Void> deleteAccount(@PathVariable String accountNumber) {
        log.info("Received request to delete account: accountNumber={}", accountNumber);
        
        accountService.deleteAccount(accountNumber);
        
        log.info("Account deleted successfully: accountNumber={}", accountNumber);
        return ResponseEntity.ok().build();
    }
} 