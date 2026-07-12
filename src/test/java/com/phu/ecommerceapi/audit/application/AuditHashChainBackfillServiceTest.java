package com.phu.ecommerceapi.audit.application;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AuditHashChainBackfillServiceTest {

    private final AuditHashService hashService = new AuditHashService();

    @Test
    void backfillAppliesHashesInIdOrderAcrossPages() {
        FakeAuditEventPersistencePort persistencePort = new FakeAuditEventPersistencePort();
        persistencePort.events = List.of(
                event(1),
                event(2),
                event(3),
                event(4),
                event(5)
        );
        AuditHashChainBackfillService service = new AuditHashChainBackfillService(
                persistencePort,
                hashService,
                new AuditHashVerificationProperties(2)
        );

        service.initializeLegacyChain();

        assertThat(persistencePort.requestedAfterIds).containsExactly(0L, 2L, 4L, 5L);
        assertThat(persistencePort.appliedHashes)
                .extracting(AppliedHash::eventId)
                .containsExactly(1L, 2L, 3L, 4L, 5L);
        assertThat(persistencePort.appliedHashes.get(0).previousHash()).isNull();
        assertThat(persistencePort.appliedHashes.get(1).previousHash())
                .isEqualTo(persistencePort.appliedHashes.get(0).eventHash());
        assertThat(persistencePort.appliedHashes.get(4).eventHash()).isEqualTo(persistencePort.markedLatestHash);
    }

    private AuditEventView event(long id) {
        return new AuditEventView(
                id,
                "audit-actor",
                "PAYMENT_SUCCEEDED",
                "PAYMENT",
                "payment-" + id,
                "amount=20.00 USD",
                "request-" + id,
                null,
                "127.0.0.1",
                "test-agent",
                Instant.parse("2026-07-06T08:00:00Z").plusSeconds(id),
                null,
                null
        );
    }

    private static final class FakeAuditEventPersistencePort implements AuditEventPersistencePort {

        private List<AuditEventView> events = List.of();
        private final List<Long> requestedAfterIds = new ArrayList<>();
        private final List<AppliedHash> appliedHashes = new ArrayList<>();
        private String markedLatestHash;

        @Override
        public List<AuditEventView> findRecentEvents(int limit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<AuditEventView> findEventsAfterId(long afterIdExclusive, int limit) {
            requestedAfterIds.add(afterIdExclusive);
            return events.stream()
                    .sorted(Comparator.comparingLong(AuditEventView::id))
                    .filter(event -> event.id() > afterIdExclusive)
                    .limit(limit)
                    .toList();
        }

        @Override
        public String latestHashForUpdate() {
            return null;
        }

        @Override
        public void applyHash(long eventId, String previousHash, String eventHash) {
            appliedHashes.add(new AppliedHash(eventId, previousHash, eventHash));
        }

        @Override
        public void markLatestHash(String latestHash, Instant updatedAt) {
            markedLatestHash = latestHash;
        }
    }

    private record AppliedHash(long eventId, String previousHash, String eventHash) {
    }
}
