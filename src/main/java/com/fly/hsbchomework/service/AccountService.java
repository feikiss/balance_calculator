package com.fly.hsbchomework.service;

import com.fly.hsbchomework.model.Account;
import com.fly.hsbchomework.model.Currency;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface AccountService {
    /**
     * 创建新账户
     */
    Account createAccount(String accountNumber, BigDecimal initialBalance, Currency currency);

    /**
     * 获取账户信息
     */
    Optional<Account> getAccount(String accountNumber);

    /**
     * 获取所有账户
     */
    List<Account> getAllAccounts();

    /**
     * 更新账户余额
     */
    Account updateBalance(String accountNumber, BigDecimal newBalance);

    /**
     * 删除账户
     */
    void deleteAccount(String accountNumber);
} 