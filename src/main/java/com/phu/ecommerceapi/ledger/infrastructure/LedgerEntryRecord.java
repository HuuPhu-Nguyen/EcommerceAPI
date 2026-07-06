package com.phu.ecommerceapi.ledger.infrastructure;

import com.phu.ecommerceapi.ledger.domain.LedgerEntryDirection;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.util.Objects;

@Entity
@Immutable
@Table(name = "ledger_entry")
public class LedgerEntryRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    private LedgerTransactionRecord transaction;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private LedgerAccountRecord account;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private LedgerEntryDirection direction;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    protected LedgerEntryRecord() {
    }

    LedgerEntryRecord(
            LedgerTransactionRecord transaction,
            LedgerAccountRecord account,
            LedgerEntryDirection direction,
            BigDecimal amount,
            String currency
    ) {
        this.transaction = Objects.requireNonNull(transaction, "ledger transaction is required");
        this.account = Objects.requireNonNull(account, "ledger account is required");
        this.direction = Objects.requireNonNull(direction, "ledger entry direction is required");
        this.amount = Objects.requireNonNull(amount, "ledger entry amount is required");
        this.currency = Objects.requireNonNull(currency, "ledger entry currency is required");
    }

    public Long getId() {
        return id;
    }

    public LedgerTransactionRecord getTransaction() {
        return transaction;
    }

    public LedgerAccountRecord getAccount() {
        return account;
    }

    public LedgerEntryDirection getDirection() {
        return direction;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }
}
