package com.phu.ecommerceapi.audit.infrastructure;

import com.phu.ecommerceapi.audit.application.AuditHashPayload;
import com.phu.ecommerceapi.audit.application.AuditEventCommand;
import com.phu.ecommerceapi.shared.api.RequestMetadata;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "audit_event")
public class AuditEventRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String actorSubject;

    @Column(nullable = false, length = 100)
    private String action;

    @Column(nullable = false, length = 100)
    private String resourceType;

    private String resourceId;

    @Column(length = 1000)
    private String details;

    @Column(nullable = false, length = 100)
    private String requestId;

    @Column(length = 64)
    private String externalCorrelationId;

    @Column(nullable = false, length = 100)
    private String ipAddress;

    @Column(nullable = false, length = 500)
    private String userAgent;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(length = 64)
    private String previousHash;

    @Column(nullable = false, length = 64)
    private String eventHash;

    @Column(length = 64)
    private String eventSignature;

    protected AuditEventRecord() {
    }

    private AuditEventRecord(
            String actorSubject,
            String action,
            String resourceType,
            String resourceId,
            String details,
            String requestId,
            String externalCorrelationId,
            String ipAddress,
            String userAgent,
            Instant createdAt
    ) {
        this.actorSubject = actorSubject;
        this.action = action;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.details = details;
        this.requestId = requestId;
        this.externalCorrelationId = externalCorrelationId;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.createdAt = createdAt;
    }

    public static AuditEventRecord from(AuditEventCommand command, RequestMetadata metadata, Instant createdAt) {
        return new AuditEventRecord(
                command.actorSubject(),
                command.action(),
                command.resourceType(),
                command.resourceId(),
                command.details(),
                metadata.requestId(),
                metadata.externalCorrelationId(),
                metadata.ipAddress(),
                metadata.userAgent(),
                createdAt
        );
    }

    public AuditHashPayload toHashPayload(String previousHash) {
        // Keep the hash payload stable for already sealed rows; external correlation is caller context.
        return new AuditHashPayload(
                previousHash,
                actorSubject,
                action,
                resourceType,
                resourceId,
                details,
                requestId,
                ipAddress,
                userAgent,
                createdAt
        );
    }

    public void applyHash(String previousHash, String eventHash) {
        this.previousHash = previousHash;
        this.eventHash = eventHash;
    }

    public void applySeal(String previousHash, String eventHash, String eventSignature) {
        applyHash(previousHash, eventHash);
        this.eventSignature = eventSignature;
    }

    public Long getId() {
        return id;
    }

    public String getActorSubject() {
        return actorSubject;
    }

    public String getAction() {
        return action;
    }

    public String getResourceType() {
        return resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public String getDetails() {
        return details;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getExternalCorrelationId() {
        return externalCorrelationId;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getPreviousHash() {
        return previousHash;
    }

    public String getEventHash() {
        return eventHash;
    }

    public String getEventSignature() {
        return eventSignature;
    }
}
