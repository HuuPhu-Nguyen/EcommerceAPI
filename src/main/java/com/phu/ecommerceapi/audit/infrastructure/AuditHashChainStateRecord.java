package com.phu.ecommerceapi.audit.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

@Entity
@Table(name = "audit_hash_chain_state")
public class AuditHashChainStateRecord {

    @Id
    private short id;

    @Column(length = 64)
    private String latestHash;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected AuditHashChainStateRecord() {
    }

    public String getLatestHash() {
        return latestHash;
    }

    public void markLatestHash(String latestHash, Instant updatedAt) {
        this.latestHash = latestHash;
        this.updatedAt = updatedAt;
    }
}
