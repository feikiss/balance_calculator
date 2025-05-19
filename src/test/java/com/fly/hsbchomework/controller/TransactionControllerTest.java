package com.fly.hsbchomework.controller;

import com.fly.hsbchomework.model.Currency;
import com.fly.hsbchomework.model.Transaction;
import com.fly.hsbchomework.model.TransactionStatus;
import com.fly.hsbchomework.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TransactionController.class)
public class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TransactionService transactionService;

    private Transaction transaction1;
    private Transaction transaction2;
    private static final String TEST_TRANSACTION_ID = "TXN-TEST-001";
    private static final int PAGE_SIZE = 10;

    @BeforeEach
    void setUp() {
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
    public void testCreateTransaction() throws Exception {
        Transaction transaction = new Transaction();
        transaction.setTransactionId(TEST_TRANSACTION_ID);
        transaction.setSourceAccountNumber("1234567890");
        transaction.setTargetAccountNumber("0987654321");
        transaction.setAmount(new BigDecimal("1000.00"));
        transaction.setCurrency(Currency.CNY);
        transaction.setStatus(TransactionStatus.PENDING);

        when(transactionService.createTransaction(anyString(), anyString(), any(BigDecimal.class), any(Currency.class)))
                .thenReturn(transaction);

        mockMvc.perform(post("/api/transactions")
                .param("sourceAccountNumber", "1234567890")
                .param("targetAccountNumber", "0987654321")
                .param("amount", "1000.00")
                .param("currency", "CNY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value(TEST_TRANSACTION_ID))
                .andExpect(jsonPath("$.sourceAccountNumber").value("1234567890"))
                .andExpect(jsonPath("$.targetAccountNumber").value("0987654321"))
                .andExpect(jsonPath("$.amount").value(1000.00))
                .andExpect(jsonPath("$.currency").value("CNY"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    public void testGetTransaction() throws Exception {
        Transaction transaction = new Transaction();
        transaction.setTransactionId(TEST_TRANSACTION_ID);
        transaction.setSourceAccountNumber("1234567890");
        transaction.setTargetAccountNumber("0987654321");
        transaction.setAmount(new BigDecimal("1000.00"));
        transaction.setCurrency(Currency.CNY);
        transaction.setStatus(TransactionStatus.COMPLETED);

        when(transactionService.getTransaction(TEST_TRANSACTION_ID)).thenReturn(Optional.of(transaction));

        mockMvc.perform(get("/api/transactions/" + TEST_TRANSACTION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value(TEST_TRANSACTION_ID))
                .andExpect(jsonPath("$.sourceAccountNumber").value("1234567890"))
                .andExpect(jsonPath("$.targetAccountNumber").value("0987654321"))
                .andExpect(jsonPath("$.amount").value(1000.00))
                .andExpect(jsonPath("$.currency").value("CNY"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    public void testProcessTransaction() throws Exception {
        Transaction transaction = new Transaction();
        transaction.setTransactionId(TEST_TRANSACTION_ID);
        transaction.setSourceAccountNumber("1234567890");
        transaction.setTargetAccountNumber("0987654321");
        transaction.setAmount(new BigDecimal("1000.00"));
        transaction.setCurrency(Currency.CNY);
        transaction.setStatus(TransactionStatus.COMPLETED);

        when(transactionService.processTransaction(TEST_TRANSACTION_ID)).thenReturn(transaction);

        mockMvc.perform(post("/api/transactions/" + TEST_TRANSACTION_ID + "/process"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value(TEST_TRANSACTION_ID))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    public void testRetryFailedTransaction() throws Exception {
        Transaction transaction = new Transaction();
        transaction.setTransactionId(TEST_TRANSACTION_ID);
        transaction.setSourceAccountNumber("1234567890");
        transaction.setTargetAccountNumber("0987654321");
        transaction.setAmount(new BigDecimal("1000.00"));
        transaction.setCurrency(Currency.CNY);
        transaction.setStatus(TransactionStatus.PENDING);

        when(transactionService.retryFailedTransaction(TEST_TRANSACTION_ID)).thenReturn(transaction);

        mockMvc.perform(post("/api/transactions/" + TEST_TRANSACTION_ID + "/retry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value(TEST_TRANSACTION_ID))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    public void testGetTransactionNotFound() throws Exception {
        when(transactionService.getTransaction(TEST_TRANSACTION_ID)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/transactions/" + TEST_TRANSACTION_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void getTransactionsByPage_ShouldReturnPagedTransactions() throws Exception {

        List<Transaction> transactions = Arrays.asList(transaction1, transaction2);
        Page<Transaction> transactionPage = new PageImpl<>(transactions, PageRequest.of(0, PAGE_SIZE), transactions.size());


        when(transactionService.getTransactionsByPage(any(PageRequest.class))).thenReturn(transactionPage);


        mockMvc.perform(get("/api/transactions/page")
                .param("page", "0")
                .param("size", String.valueOf(PAGE_SIZE))
                .param("sortBy", "id")
                .param("direction", "desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(PAGE_SIZE))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].transactionId").value("TXN-001"))
                .andExpect(jsonPath("$.content[1].transactionId").value("TXN-002"));
    }

    @Test
    void getTransactionsByAccountNumber_ShouldReturnPagedTransactions() throws Exception {

        List<Transaction> transactions = Arrays.asList(transaction1);
        Page<Transaction> transactionPage = new PageImpl<>(transactions, PageRequest.of(0, PAGE_SIZE), transactions.size());
        String accountNumber = "ACC-001";


        when(transactionService.getTransactionsByAccountNumber(any(String.class), any(PageRequest.class)))
                .thenReturn(transactionPage);


        mockMvc.perform(get("/api/transactions/account/" + accountNumber + "/page")
                .param("page", "0")
                .param("size", String.valueOf(PAGE_SIZE))
                .param("sortBy", "id")
                .param("direction", "desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(PAGE_SIZE))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].transactionId").value("TXN-001"))
                .andExpect(jsonPath("$.content[0].sourceAccountNumber").value("ACC-001"));
    }

    @Test
    void getTransactionsByPage_WithEmptyResult_ShouldReturnEmptyPage() throws Exception {
        Page<Transaction> emptyPage = new PageImpl<>(Arrays.asList(), PageRequest.of(0, PAGE_SIZE), 0);

        when(transactionService.getTransactionsByPage(any(PageRequest.class))).thenReturn(emptyPage);


        mockMvc.perform(get("/api/transactions/page")
                .param("page", "0")
                .param("size", String.valueOf(PAGE_SIZE))
                .param("sortBy", "id")
                .param("direction", "desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(PAGE_SIZE))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void getTransactionsByAccountNumber_WithEmptyResult_ShouldReturnEmptyPage() throws Exception {
        // data
        Page<Transaction> emptyPage = new PageImpl<>(Arrays.asList(), PageRequest.of(0, PAGE_SIZE), 0);
        String accountNumber = "NON-EXISTENT";

        // mock
        when(transactionService.getTransactionsByAccountNumber(any(String.class), any(PageRequest.class)))
                .thenReturn(emptyPage);

        // tets
        mockMvc.perform(get("/api/transactions/account/" + accountNumber + "/page")
                .param("page", "0")
                .param("size", String.valueOf(PAGE_SIZE))
                .param("sortBy", "id")
                .param("direction", "desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(PAGE_SIZE))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void getTransactionsByPage_WithInvalidSortDirection_ShouldUseDefaultDirection() throws Exception {
        // data
        List<Transaction> transactions = Arrays.asList(transaction1, transaction2);
        Page<Transaction> transactionPage = new PageImpl<>(transactions, PageRequest.of(0, PAGE_SIZE), transactions.size());

        // mock
        when(transactionService.getTransactionsByPage(any(PageRequest.class))).thenReturn(transactionPage);

        // tsest
        mockMvc.perform(get("/api/transactions/page")
                .param("page", "0")
                .param("size", String.valueOf(PAGE_SIZE))
                .param("sortBy", "id")
                .param("direction", "desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.size").value(PAGE_SIZE))
                .andExpect(jsonPath("$.totalElements").value(2));
    }
} 