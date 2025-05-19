package com.fly.hsbchomework.repository;

import com.fly.hsbchomework.model.Account;
import com.fly.hsbchomework.model.Currency;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
public class AccountRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private AccountRepository accountRepository;

    @Test
    public void testSaveAndFindAccount() {
        // 创建测试账户
        Account account = new Account();
        account.setAccountNumber("1234567890");
        account.setBalance(new BigDecimal("1000.00"));
        account.setCurrency(Currency.CNY);

        // 保存账户
        entityManager.persist(account);
        entityManager.flush();

        // 查找账户
        Optional<Account> found = accountRepository.findByAccountNumber("1234567890");

        // 验证结果
        assertThat(found).isPresent();
        assertThat(found.get().getAccountNumber()).isEqualTo("1234567890");
        assertThat(found.get().getBalance()).isEqualTo(new BigDecimal("1000.00"));
        assertThat(found.get().getCurrency()).isEqualTo(Currency.CNY);
    }

    @Test
    public void testFindByAccountNumber() {
        // 创建测试账户
        Account account = new Account();
        account.setAccountNumber("1234567890");
        account.setBalance(new BigDecimal("1000.00"));
        account.setCurrency(Currency.CNY);
        entityManager.persist(account);
        entityManager.flush();

        // 通过账号查找
        Optional<Account> foundAccount = accountRepository.findByAccountNumber("1234567890");
        assertThat(foundAccount).isPresent();
        assertThat(foundAccount.get().getAccountNumber()).isEqualTo("1234567890");
    }

    @Test
    public void testUpdateAccountBalance() {
        // 创建测试账户
        Account account = new Account();
        account.setAccountNumber("1234567890");
        account.setBalance(new BigDecimal("1000.00"));
        account.setCurrency(Currency.CNY);

        // 保存账户
        entityManager.persist(account);
        entityManager.flush();

        // 更新余额
        account.setBalance(new BigDecimal("2000.00"));
        entityManager.persist(account);
        entityManager.flush();

        // 查找账户
        Optional<Account> found = accountRepository.findByAccountNumber("1234567890");

        // 验证结果
        assertThat(found).isPresent();
        assertThat(found.get().getBalance()).isEqualTo(new BigDecimal("2000.00"));
    }

    @Test
    public void testDeleteAccount() {
        // create
        Account account = new Account();
        account.setAccountNumber("1234567890");
        account.setBalance(new BigDecimal("1000.00"));
        account.setCurrency(Currency.CNY);
        Account savedAccount = accountRepository.save(account);

        accountRepository.delete(savedAccount);

        Optional<Account> foundAccount = accountRepository.findById(savedAccount.getId());
        assertThat(foundAccount).isEmpty();
    }

    @Test
    public void testFindAllAccounts() {
        Account account1 = new Account();
        account1.setAccountNumber("1234567890");
        account1.setBalance(new BigDecimal("1000.00"));
        account1.setCurrency(Currency.CNY);

        Account account2 = new Account();
        account2.setAccountNumber("0987654321");
        account2.setBalance(new BigDecimal("2000.00"));
        account2.setCurrency(Currency.CNY);
        accountRepository.save(account1);
        accountRepository.save(account2);

        assertThat(accountRepository.findAll()).hasSize(2);
    }
} 