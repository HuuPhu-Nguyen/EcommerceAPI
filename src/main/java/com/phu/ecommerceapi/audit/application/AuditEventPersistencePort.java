package com.phu.ecommerceapi.audit.application;

import java.time.Instant;
import java.util.List;

public interface AuditEventPersistencePort {

    List<AuditEventView> findRecentEvents(int limit);

    List<AuditEventView> findEventsAfterId(long afterIdExclusive, int limit);

    String latestHashForUpdate();

    void applyHash(long eventId, String previousHash, String eventHash);

    void markLatestHash(String latestHash, Instant updatedAt);
}
