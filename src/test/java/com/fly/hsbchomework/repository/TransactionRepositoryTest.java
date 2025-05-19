package com.fly.hsbchomework.repository;

import com.fly.hsbchomework.model.Currency;
import com.fly.hsbchomework.model.Transaction;
import com.fly.hsbchomework.model.TransactionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
public class TransactionRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private TransactionRepository transactionRepository;

    @Test
    public void testSaveAndFindTransaction() {
        // 创建测试交易
        Transaction transaction = new Transaction();
        transaction.setTransactionId("T123456");
        transaction.setSourceAccountNumber("1234567890");
        transaction.setTargetAccountNumber("0987654321");
        transaction.setAmount(new BigDecimal("100.00"));
        transaction.setCurrency(Currency.CNY);
        transaction.setStatus(TransactionStatus.PENDING);

        // 保存交易
        entityManager.persist(transaction);
        entityManager.flush();

        // 查找交易
        List<Transaction> found = transactionRepository.findBySourceAccountNumber("1234567890");

        // 验证结果
        assertThat(found).hasSize(1);
        assertThat(found.get(0).getTransactionId()).isEqualTo("T123456");
        assertThat(found.get(0).getAmount()).isEqualTo(new BigDecimal("100.00"));
        assertThat(found.get(0).getStatus()).isEqualTo(TransactionStatus.PENDING);
    }

    @Test
    public void testFindByStatus() {
        // 创建测试交易
        Transaction transaction = new Transaction();
        transaction.setTransactionId("T123456");
        transaction.setSourceAccountNumber("1234567890");
        transaction.setTargetAccountNumber("0987654321");
        transaction.setAmount(new BigDecimal("100.00"));
        transaction.setCurrency(Currency.CNY);
        transaction.setStatus(TransactionStatus.PENDING);

        // 保存交易
        entityManager.persist(transaction);
        entityManager.flush();

        // 查找待处理交易
        List<Transaction> pendingTransactions = transactionRepository.findByStatus(TransactionStatus.PENDING);

        // 验证结果
        assertThat(pendingTransactions).hasSize(1);
        assertThat(pendingTransactions.get(0).getTransactionId()).isEqualTo("T123456");
    }

    @Test
    public void testUpdateTransactionStatus() {
        // 创建测试交易
        Transaction transaction = new Transaction();
        transaction.setTransactionId("TEST-001");
        transaction.setSourceAccountNumber("1234567890");
        transaction.setTargetAccountNumber("0987654321");
        transaction.setAmount(new BigDecimal("100.00"));
        transaction.setCurrency(Currency.CNY);
        transaction.setStatus(TransactionStatus.PENDING);
        Transaction savedTransaction = transactionRepository.save(transaction);

        // 更新状态
        savedTransaction.setStatus(TransactionStatus.COMPLETED);
        Transaction updatedTransaction = transactionRepository.save(savedTransaction);

        // 验证更新
        Optional<Transaction> foundTransaction = transactionRepository.findById(updatedTransaction.getId());
        assertThat(foundTransaction).isPresent();
        assertThat(foundTransaction.get().getStatus()).isEqualTo(TransactionStatus.COMPLETED);
    }

    @Test
    public void testFindByAccountNumber() {
        // 创建测试交易
        Transaction transaction = new Transaction();
        transaction.setTransactionId("TEST-001");
        transaction.setSourceAccountNumber("1234567890");
        transaction.setTargetAccountNumber("0987654321");
        transaction.setAmount(new BigDecimal("100.00"));
        transaction.setCurrency(Currency.CNY);
        transaction.setStatus(TransactionStatus.PENDING);
        transactionRepository.save(transaction);


        List<Transaction> sourceTransactions = transactionRepository.findBySourceAccountNumber("1234567890");
        assertThat(sourceTransactions).hasSize(1);
        assertThat(sourceTransactions.get(0).getAmount()).isEqualTo(new BigDecimal("100.00"));


        List<Transaction> targetTransactions = transactionRepository.findByTargetAccountNumber("0987654321");
        assertThat(targetTransactions).hasSize(1);
        assertThat(targetTransactions.get(0).getAmount()).isEqualTo(new BigDecimal("100.00"));
    }

    @Test
    public void testDeleteTransaction() {

        Transaction transaction = new Transaction();
        transaction.setTransactionId("TEST-001");
        transaction.setSourceAccountNumber("1234567890");
        transaction.setTargetAccountNumber("0987654321");
        transaction.setAmount(new BigDecimal("100.00"));
        transaction.setCurrency(Currency.CNY);
        transaction.setStatus(TransactionStatus.PENDING);
        Transaction savedTransaction = transactionRepository.save(transaction);


        transactionRepository.delete(savedTransaction);


        Optional<Transaction> foundTransaction = transactionRepository.findById(savedTransaction.getId());
        assertThat(foundTransaction).isEmpty();
    }
} 