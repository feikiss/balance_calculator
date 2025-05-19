package com.fly.hsbchomework.service;

import com.fly.hsbchomework.model.Account;
import com.fly.hsbchomework.model.Currency;
import com.fly.hsbchomework.model.Transaction;
import com.fly.hsbchomework.model.TransactionStatus;
import com.fly.hsbchomework.repository.AccountRepository;
import com.fly.hsbchomework.repository.TransactionRepository;
import com.fly.hsbchomework.service.impl.TransactionServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class TransactionServiceTest {

    @Mock
    private AccountService accountService;
    
    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private TransactionServiceImpl transactionService;

    private Transaction transaction1;
    private Transaction transaction2;

    @BeforeEach
    void setUp() {
        // 初始化测试数据
        transaction1 = new Transaction();
        transaction1.setTransactionId("TXN-001");
        transaction1.setSourceAccountNumber("ACC-001");
        transaction1.setTargetAccountNumber("ACC-002");
        transaction1.setAmount(new BigDecimal("100.00"));
        transaction1.setCurrency(Currency.CNY);
        transaction1.setStatus(TransactionStatus.COMPLETED);

        transaction2 = new Transaction();
        transaction2.setTransactionId("TXN-002");
        transaction2.setSourceAccountNumber("ACC-002");
        transaction2.setTargetAccountNumber("ACC-003");
        transaction2.setAmount(new BigDecimal("200.00"));
        transaction2.setCurrency(Currency.CNY);
        transaction2.setStatus(TransactionStatus.COMPLETED);
    }

    @Test
    void getTransactionsByPage_ShouldReturnPagedTransactions() {
        // 准备测试数据
        List<Transaction> transactions = Arrays.asList(transaction1, transaction2);
        Page<Transaction> transactionPage = new PageImpl<>(transactions);
        PageRequest pageRequest = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "id"));

        // 配置mock行为
        when(transactionRepository.findAll(pageRequest)).thenReturn(transactionPage);

        // 执行测试
        Page<Transaction> result = transactionService.getTransactionsByPage(pageRequest);

        // 验证结果
        assertNotNull(result);
        assertEquals(2, result.getContent().size());
        assertEquals(0, result.getNumber());
        assertEquals(2, result.getSize());
        assertTrue(result.getContent().contains(transaction1));
        assertTrue(result.getContent().contains(transaction2));
    }

    @Test
    void getTransactionsByAccountNumber_ShouldReturnPagedTransactions() {
        // 准备测试数据
        List<Transaction> transactions = Arrays.asList(transaction1);
        Page<Transaction> transactionPage = new PageImpl<>(transactions);
        PageRequest pageRequest = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "id"));
        String accountNumber = "ACC-001";

        // 配置mock行为
        when(transactionRepository.findBySourceAccountNumberOrTargetAccountNumber(
                accountNumber, accountNumber, pageRequest)).thenReturn(transactionPage);

        // 执行测试
        Page<Transaction> result = transactionService.getTransactionsByAccountNumber(accountNumber, pageRequest);

        // 验证结果
        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals(0, result.getNumber());
        assertEquals(1, result.getSize());
        assertTrue(result.getContent().contains(transaction1));
        assertEquals(accountNumber, result.getContent().get(0).getSourceAccountNumber());
    }

    @Test
    void getTransactionsByPage_WithEmptyResult_ShouldReturnEmptyPage() {
        // 准备测试数据
        Page<Transaction> emptyPage = Page.empty();
        PageRequest pageRequest = PageRequest.of(0, 10);

        // 配置mock行为
        when(transactionRepository.findAll(pageRequest)).thenReturn(emptyPage);

        // 执行测试
        Page<Transaction> result = transactionService.getTransactionsByPage(pageRequest);

        // 验证结果
        assertNotNull(result);
        assertTrue(result.getContent().isEmpty());
        assertEquals(0, result.getNumber());
        assertEquals(0, result.getSize());
    }

    @Test
    void getTransactionsByAccountNumber_WithEmptyResult_ShouldReturnEmptyPage() {
        // 准备测试数据
        Page<Transaction> emptyPage = Page.empty();
        PageRequest pageRequest = PageRequest.of(0, 10);
        String accountNumber = "NON-EXISTENT";

        // 配置mock行为
        when(transactionRepository.findBySourceAccountNumberOrTargetAccountNumber(
                accountNumber, accountNumber, pageRequest)).thenReturn(emptyPage);

        // 执行测试
        Page<Transaction> result = transactionService.getTransactionsByAccountNumber(accountNumber, pageRequest);

        // 验证结果
        assertNotNull(result);
        assertTrue(result.getContent().isEmpty());
        assertEquals(0, result.getNumber());
        assertEquals(0, result.getSize());
    }
} 