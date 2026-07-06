package com.phu.ecommerceapi.audit.infrastructure;

import com.phu.ecommerceapi.audit.application.AuditEventCommand;
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

    @Column(nullable = false)
    private Instant createdAt;

    protected AuditEventRecord() {
    }

    private AuditEventRecord(
            String actorSubject,
            String action,
            String resourceType,
            String resourceId,
            String details,
            Instant createdAt
    ) {
        this.actorSubject = actorSubject;
        this.action = action;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.details = details;
        this.createdAt = createdAt;
    }

    public static AuditEventRecord from(AuditEventCommand command, Instant createdAt) {
        return new AuditEventRecord(
                command.actorSubject(),
                command.action(),
                command.resourceType(),
                command.resourceId(),
                command.details(),
                createdAt
        );
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

    public Instant getCreatedAt() {
        return createdAt;
    }
}
