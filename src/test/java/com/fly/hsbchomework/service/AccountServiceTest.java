package com.fly.hsbchomework.service;

import com.fly.hsbchomework.model.Account;
import com.fly.hsbchomework.model.Currency;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.Optional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class AccountServiceTest {

    @Autowired
    private AccountService accountService;

    @Test
    public void testCreateAndGetAccount() {
        String accountNumber = "A10001";
        Account account = accountService.createAccount(accountNumber, new BigDecimal("1000.00"), Currency.CNY);
        Assertions.assertNotNull(account.getId());
        Optional<Account> found = accountService.getAccount(accountNumber);
        Assertions.assertTrue(found.isPresent());
        Assertions.assertEquals(accountNumber, found.get().getAccountNumber());
    }

    @Test
    public void testUpdateBalance() {
        String accountNumber = "A10002";
        accountService.createAccount(accountNumber, new BigDecimal("500.00"), Currency.CNY);
        Account updated = accountService.updateBalance(accountNumber, new BigDecimal("200.00"));
        Assertions.assertEquals(new BigDecimal("700.00"), updated.getBalance());
    }
} 