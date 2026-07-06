package com.phu.ecommerceapi.payment.application;

import com.phu.ecommerceapi.payment.infrastructure.PaymentIdempotencyRecord;
import com.phu.ecommerceapi.payment.infrastructure.PaymentIdempotencyRecordRepository;
import com.phu.ecommerceapi.shared.api.ConflictException;
import com.phu.ecommerceapi.shared.observability.BusinessMetrics;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class PaymentIdempotencyService {

    private final PaymentIdempotencyRecordRepository repository;
    private final BusinessMetrics businessMetrics;

    public PaymentIdempotencyService(
            PaymentIdempotencyRecordRepository repository,
            BusinessMetrics businessMetrics
    ) {
        this.repository = repository;
        this.businessMetrics = businessMetrics;
    }

    @Transactional
    public PaymentIdempotencyDecision start(PaymentIdempotencyCommand command) {
        String requestHash = hash(command.requestBody());
        int insertedRows = repository.insertInProgress(
                command.customerId(),
                command.endpoint(),
                command.operation(),
                command.idempotencyKey(),
                requestHash,
                OffsetDateTime.now()
        );

        PaymentIdempotencyRecord record = repository
                .findByCustomerIdAndEndpointAndOperationAndIdempotencyKey(
                        command.customerId(),
                        command.endpoint(),
                        command.operation(),
                        command.idempotencyKey()
                )
                .orElseThrow(() -> new IllegalStateException("Idempotency record was not persisted"));

        if (insertedRows == 1) {
            PaymentIdempotencyDecision decision = PaymentIdempotencyDecision.started(record.getId());
            recordDecision(decision.type());
            return decision;
        }
        return existingDecision(record, requestHash);
    }

    @Transactional(readOnly = true)
    public Optional<PaymentIdempotencyDecision> findExisting(PaymentIdempotencyCommand command) {
        String requestHash = hash(command.requestBody());
        return repository.findByCustomerIdAndEndpointAndOperationAndIdempotencyKey(
                        command.customerId(),
                        command.endpoint(),
                        command.operation(),
                        command.idempotencyKey()
                )
                .map(record -> existingDecision(record, requestHash));
    }

    @Transactional
    public void complete(long recordId, int responseStatus, String responseBody) {
        PaymentIdempotencyRecord record = repository.findById(recordId)
                .orElseThrow(() -> new IllegalArgumentException("Idempotency record not found"));
        record.complete(responseStatus, responseBody);
    }

    private PaymentIdempotencyDecision existingDecision(PaymentIdempotencyRecord record, String requestHash) {
        if (!record.hasRequestHash(requestHash)) {
            businessMetrics.idempotencyDecision("CONFLICT");
            throw new ConflictException("Idempotency key was reused with a different request body");
        }
        if (record.isCompleted()) {
            PaymentIdempotencyDecision decision = PaymentIdempotencyDecision.replay(
                    record.getId(),
                    record.getResponseStatus(),
                    record.getResponseBody()
            );
            recordDecision(decision.type());
            return decision;
        }
        PaymentIdempotencyDecision decision = PaymentIdempotencyDecision.inProgress(record.getId());
        recordDecision(decision.type());
        return decision;
    }

    private void recordDecision(PaymentIdempotencyDecisionType decisionType) {
        businessMetrics.idempotencyDecision(decisionType.name());
    }

    private String hash(String requestBody) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(requestBody.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
