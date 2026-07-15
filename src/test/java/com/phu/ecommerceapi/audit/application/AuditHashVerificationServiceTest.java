package com.phu.ecommerceapi.audit.application;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AuditHashVerificationServiceTest {

    private static final String SIGNATURE_SECRET = "test-audit-signature-secret";

    private final AuditHashService hashService = new AuditHashService(SIGNATURE_SECRET);

    @Test
    void verifyRequestsEveryPageInOrder() {
        FakeAuditEventPersistencePort persistencePort = new FakeAuditEventPersistencePort();
        persistencePort.events = sealedChain(1, 2, 3, 4, 5);
        AuditHashVerificationService service = service(persistencePort, 2);

        AuditHashVerificationResult result = service.verify();

        assertThat(result.verified()).isTrue();
        assertThat(result.checkedEvents()).isEqualTo(5);
        assertThat(result.latestHash()).isEqualTo(persistencePort.events.getLast().eventHash());
        assertThat(persistencePort.requestedAfterIds).containsExactly(0L, 2L, 4L, 5L);
    }

    @Test
    void verifyStopsWhenSecondPageContainsBrokenPreviousHash() {
        FakeAuditEventPersistencePort persistencePort = new FakeAuditEventPersistencePort();
        List<AuditEventView> events = sealedChain(1, 2, 3);
        AuditEventView brokenEvent = event(
                2,
                "broken-previous-hash",
                hashService.hash(events.get(1).toHashPayload("broken-previous-hash"))
        );
        persistencePort.events = List.of(events.get(0), brokenEvent, events.get(2));
        AuditHashVerificationService service = service(persistencePort, 1);

        AuditHashVerificationResult result = service.verify();

        assertThat(result.verified()).isFalse();
        assertThat(result.checkedEvents()).isEqualTo(2);
        assertThat(result.brokenEventId()).isEqualTo(2);
        assertThat(result.message()).isEqualTo("Audit event previous hash does not match chain");
        assertThat(persistencePort.requestedAfterIds).containsExactly(0L, 1L);
    }

    @Test
    void verifyUsesLatestHashFromSmallerLastPage() {
        FakeAuditEventPersistencePort persistencePort = new FakeAuditEventPersistencePort();
        persistencePort.events = sealedChain(1, 2, 3);
        AuditHashVerificationService service = service(persistencePort, 2);

        AuditHashVerificationResult result = service.verify();

        assertThat(result.verified()).isTrue();
        assertThat(result.checkedEvents()).isEqualTo(3);
        assertThat(result.latestHash()).isEqualTo(persistencePort.events.getLast().eventHash());
        assertThat(persistencePort.requestedAfterIds).containsExactly(0L, 2L, 3L);
    }

    @Test
    void verifyStopsWhenSignatureDoesNotMatchEventHash() {
        FakeAuditEventPersistencePort persistencePort = new FakeAuditEventPersistencePort();
        List<AuditEventView> events = sealedChain(1, 2);
        AuditEventView brokenEvent = event(
                2,
                events.get(1).previousHash(),
                events.get(1).eventHash(),
                "0".repeat(64)
        );
        persistencePort.events = List.of(events.get(0), brokenEvent);
        AuditHashVerificationService service = service(persistencePort, 2);

        AuditHashVerificationResult result = service.verify();

        assertThat(result.verified()).isFalse();
        assertThat(result.checkedEvents()).isEqualTo(2);
        assertThat(result.brokenEventId()).isEqualTo(2);
        assertThat(result.message()).isEqualTo("Audit event signature mismatch");
    }

    @Test
    void verifyAllowsLegacyEventsWithNullSignature() {
        FakeAuditEventPersistencePort persistencePort = new FakeAuditEventPersistencePort();
        persistencePort.events = sealedChain(1, 2).stream()
                .map(event -> event(event.id(), event.previousHash(), event.eventHash(), null))
                .toList();
        AuditHashVerificationService service = service(persistencePort, 2);

        AuditHashVerificationResult result = service.verify();

        assertThat(result.verified()).isTrue();
        assertThat(result.checkedEvents()).isEqualTo(2);
    }

    private AuditHashVerificationService service(FakeAuditEventPersistencePort persistencePort, int batchSize) {
        return new AuditHashVerificationService(
                persistencePort,
                hashService,
                new AuditHashVerificationProperties(batchSize)
        );
    }

    private List<AuditEventView> sealedChain(long... eventIds) {
        List<AuditEventView> events = new ArrayList<>();
        String previousHash = null;
        for (long eventId : eventIds) {
            AuditEventView event = event(eventId, previousHash, null);
            String eventHash = hashService.hash(event.toHashPayload(previousHash));
            events.add(event(eventId, previousHash, eventHash));
            previousHash = eventHash;
        }
        return List.copyOf(events);
    }

    private AuditEventView event(long id, String previousHash, String eventHash) {
        return event(
                id,
                previousHash,
                eventHash,
                eventHash == null ? null : hashService.sign(eventHash)
        );
    }

    private AuditEventView event(long id, String previousHash, String eventHash, String eventSignature) {
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
                previousHash,
                eventHash,
                eventSignature
        );
    }

    private static final class FakeAuditEventPersistencePort implements AuditEventPersistencePort {

        private List<AuditEventView> events = List.of();
        private final List<Long> requestedAfterIds = new ArrayList<>();

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
            throw new UnsupportedOperationException();
        }

        @Override
        public void applyHash(long eventId, String previousHash, String eventHash) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void markLatestHash(String latestHash, Instant updatedAt) {
            throw new UnsupportedOperationException();
        }
    }
}
