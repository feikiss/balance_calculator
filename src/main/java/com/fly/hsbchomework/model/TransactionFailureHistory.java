package com.fly.hsbchomework.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "transaction_failure_histories")
@NoArgsConstructor
@AllArgsConstructor
public class TransactionFailureHistory implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", nullable = false)
    private Transaction transaction;

    @Column(nullable = false)
    private Integer retryCount;

    @Column(nullable = false)
    private LocalDateTime failureTime;

    @Column(length = 1000, nullable = false)
    private String failureReason;

    @PrePersist
    protected void onCreate() {
        failureTime = LocalDateTime.now();
    }
} 