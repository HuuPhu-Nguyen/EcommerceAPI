package com.phu.ecommerceapi.ledger.infrastructure;

import com.phu.ecommerceapi.ledger.domain.LedgerEntryDirection;
import com.phu.ecommerceapi.ledger.domain.LedgerTransactionType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Immutable
@Table(name = "ledger_transaction")
public class LedgerTransactionRecord {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private LedgerTransactionType transactionType;

    @Column(nullable = false, length = 100)
    private String referenceType;

    @Column(nullable = false, length = 100)
    private String referenceId;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(nullable = false)
    private OffsetDateTime postedAt;

    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL, orphanRemoval = false)
    private List<LedgerEntryRecord> entries = new ArrayList<>();

    protected LedgerTransactionRecord() {
    }

    private LedgerTransactionRecord(
            LedgerTransactionType transactionType,
            String referenceType,
            String referenceId,
            String description,
            OffsetDateTime postedAt
    ) {
        this.id = UUID.randomUUID();
        this.transactionType = Objects.requireNonNull(transactionType, "ledger transaction type is required");
        this.referenceType = Objects.requireNonNull(referenceType, "ledger reference type is required");
        this.referenceId = Objects.requireNonNull(referenceId, "ledger reference id is required");
        this.description = Objects.requireNonNull(description, "ledger description is required");
        this.postedAt = Objects.requireNonNull(postedAt, "ledger posted time is required");
    }

    public static LedgerTransactionRecord posted(
            LedgerTransactionType transactionType,
            String referenceType,
            String referenceId,
            String description,
            OffsetDateTime postedAt
    ) {
        return new LedgerTransactionRecord(transactionType, referenceType, referenceId, description, postedAt);
    }

    public void addEntry(
            LedgerAccountRecord account,
            LedgerEntryDirection direction,
            BigDecimal amount,
            String currency
    ) {
        entries.add(new LedgerEntryRecord(this, account, direction, amount, currency));
    }

    public UUID getId() {
        return id;
    }

    public LedgerTransactionType getTransactionType() {
        return transactionType;
    }

    public String getReferenceType() {
        return referenceType;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public String getDescription() {
        return description;
    }

    public OffsetDateTime getPostedAt() {
        return postedAt;
    }

    public List<LedgerEntryRecord> getEntries() {
        return List.copyOf(entries);
    }
}
