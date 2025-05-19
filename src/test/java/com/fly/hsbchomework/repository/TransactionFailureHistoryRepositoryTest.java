package com.fly.hsbchomework.repository;

import com.fly.hsbchomework.model.Currency;
import com.fly.hsbchomework.model.Transaction;
import com.fly.hsbchomework.model.TransactionFailureHistory;
import com.fly.hsbchomework.model.TransactionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
public class TransactionFailureHistoryRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private TransactionFailureHistoryRepository failureHistoryRepository;

    @Test
    public void testSaveAndFindFailureHistory() {

        Transaction transaction = new Transaction();
        transaction.setTransactionId("TEST-001");
        transaction.setSourceAccountNumber("ACC-001");
        transaction.setTargetAccountNumber("ACC-002");
        transaction.setAmount(new BigDecimal("100.00"));
        transaction.setCurrency(Currency.CNY);
        transaction.setStatus(TransactionStatus.FAILED);
        transaction.setCreatedAt(LocalDateTime.now());
        transaction.setUpdatedAt(LocalDateTime.now());
        transaction.setRetryCount(1);
        transaction.setMaxRetryCount(3);
        transaction.setLastRetryTime(LocalDateTime.now());
        entityManager.persist(transaction);


        TransactionFailureHistory failureHistory = new TransactionFailureHistory();
        failureHistory.setTransaction(transaction);
        failureHistory.setRetryCount(1);
        failureHistory.setFailureTime(LocalDateTime.now());
        failureHistory.setFailureReason("余额不足");
        entityManager.persist(failureHistory);


        List<TransactionFailureHistory> histories = failureHistoryRepository.findByTransactionIdOrderByFailureTimeDesc(transaction.getId());


        assertThat(histories).hasSize(1);
        assertThat(histories.get(0).getFailureReason()).isEqualTo("余额不足");
        assertThat(histories.get(0).getRetryCount()).isEqualTo(1);
    }

    @Test
    public void testFindMultipleFailureHistories() {
        // 创建测试交易
        Transaction transaction = new Transaction();
        transaction.setTransactionId("TEST-002");
        transaction.setSourceAccountNumber("ACC-001");
        transaction.setTargetAccountNumber("ACC-002");
        transaction.setAmount(new BigDecimal("100.00"));
        transaction.setCurrency(Currency.CNY);
        transaction.setStatus(TransactionStatus.FAILED);
        transaction.setCreatedAt(LocalDateTime.now());
        transaction.setUpdatedAt(LocalDateTime.now());
        transaction.setRetryCount(2);
        transaction.setMaxRetryCount(3);
        transaction.setLastRetryTime(LocalDateTime.now());
        entityManager.persist(transaction);


        TransactionFailureHistory failureHistory1 = new TransactionFailureHistory();
        failureHistory1.setTransaction(transaction);
        failureHistory1.setRetryCount(1);
        failureHistory1.setFailureTime(LocalDateTime.now().minusMinutes(5));
        failureHistory1.setFailureReason("First time fail：lack of money");
        entityManager.persist(failureHistory1);

        TransactionFailureHistory failureHistory2 = new TransactionFailureHistory();
        failureHistory2.setTransaction(transaction);
        failureHistory2.setRetryCount(2);
        failureHistory2.setFailureTime(LocalDateTime.now());
        failureHistory2.setFailureReason("second time fail：system errro");
        entityManager.persist(failureHistory2);


        List<TransactionFailureHistory> histories = failureHistoryRepository.findByTransactionIdOrderByFailureTimeDesc(transaction.getId());


        assertThat(histories).hasSize(2);
        assertThat(histories.get(0).getFailureReason()).isEqualTo("second time fail：system errro");
        assertThat(histories.get(1).getFailureReason()).isEqualTo("First time fail：lack of money");
        assertThat(histories.get(0).getRetryCount()).isEqualTo(2);
        assertThat(histories.get(1).getRetryCount()).isEqualTo(1);
    }
} 