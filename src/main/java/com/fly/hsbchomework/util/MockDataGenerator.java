package com.fly.hsbchomework.util;

import com.fly.hsbchomework.model.Currency;
import com.fly.hsbchomework.service.AccountService;
import com.fly.hsbchomework.service.TransactionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.Random;

@Component
public class MockDataGenerator implements CommandLineRunner {
    @Autowired
    private AccountService accountService;
    @Autowired
    private TransactionService transactionService;

    @Override
    public void run(String... args) throws Exception {
        // 只在指定参数下生成数据，避免每次启动都生成
        if (args.length > 0 && "mockdata".equals(args[0])) {
            generateAccounts(100);
            generateTransactions(500);
            System.out.println("模拟数据生成完毕");
        }
    }

    private void generateAccounts(int count) {
        for (int i = 1; i <= count; i++) {
            String accountNumber = String.format("M%05d", i);
            accountService.createAccount(accountNumber, new BigDecimal(1000 + i * 10), Currency.CNY);
        }
    }

    private void generateTransactions(int count) {
        Random random = new Random();
        for (int i = 0; i < count; i++) {
            String source = String.format("M%05d", random.nextInt(100) + 1);
            String target = String.format("M%05d", random.nextInt(100) + 1);
            if (source.equals(target)) continue;
            BigDecimal amount = new BigDecimal(random.nextInt(100) + 1);
            transactionService.createTransaction(source, target, amount, Currency.CNY);
        }
    }
} 