package com.fly.hsbchomework.controller;

import com.fly.hsbchomework.model.Account;
import com.fly.hsbchomework.model.Currency;
import com.fly.hsbchomework.service.AccountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AccountController.class)
public class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AccountService accountService;

    @Test
    public void testCreateAccount() throws Exception {
        Account account = new Account();
        account.setAccountNumber("1234567890");
        account.setBalance(new BigDecimal("1000.00"));
        account.setCurrency(Currency.CNY);

        when(accountService.createAccount(anyString(), any(BigDecimal.class), any(Currency.class))).thenReturn(account);

        mockMvc.perform(post("/api/accounts")
                .param("accountNumber", "1234567890")
                .param("initialBalance", "1000.00")
                .param("currency", "CNY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountNumber").value("1234567890"))
                .andExpect(jsonPath("$.balance").value(1000.00))
                .andExpect(jsonPath("$.currency").value(Currency.CNY.toString()));
    }

    @Test
    public void testGetAccount() throws Exception {
        Account account = new Account();
        account.setAccountNumber("1234567890");
        account.setBalance(new BigDecimal("1000.00"));
        account.setCurrency(Currency.CNY);

        when(accountService.getAccount("1234567890")).thenReturn(Optional.of(account));

        mockMvc.perform(get("/api/accounts/1234567890"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountNumber").value("1234567890"))
                .andExpect(jsonPath("$.balance").value(1000.00))
                .andExpect(jsonPath("$.currency").value(Currency.CNY.toString()));
    }

    @Test
    public void testUpdateBalance() throws Exception {
        Account account = new Account();
        account.setAccountNumber("1234567890");
        account.setBalance(new BigDecimal("2000.00"));
        account.setCurrency(Currency.CNY);

        when(accountService.updateBalance("1234567890", new BigDecimal("2000.00"))).thenReturn(account);

        mockMvc.perform(put("/api/accounts/1234567890/balance")
                .param("amount", "2000.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountNumber").value("1234567890"))
                .andExpect(jsonPath("$.balance").value(2000.00))
                .andExpect(jsonPath("$.currency").value(Currency.CNY.toString()));
    }

    @Test
    public void testGetAccountNotFound() throws Exception {
        when(accountService.getAccount("9999999999")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/accounts/9999999999"))
                .andExpect(status().isNotFound());
    }
} 