package com.phu.ecommerceapi.payment.application;

import com.phu.ecommerceapi.User.UserModel;
import com.phu.ecommerceapi.User.UserRepo;
import com.phu.ecommerceapi.payment.infrastructure.PaymentIdempotencyRecord;
import com.phu.ecommerceapi.payment.infrastructure.PaymentIdempotencyRecordRepository;
import com.phu.ecommerceapi.shared.api.ConflictException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class PaymentIdempotencyServiceTest {

    @Autowired
    private PaymentIdempotencyService service;

    @Autowired
    private PaymentIdempotencyRecordRepository repository;

    @Autowired
    private UserRepo userRepo;

    @BeforeEach
    void resetData() {
        repository.deleteAll();
        userRepo.deleteAll();
    }

    @AfterEach
    void cleanUpData() {
        resetData();
    }

    @Test
    void completedRequestReplaysStoredResponseForSameBody() {
        UserModel customer = customer("replay-customer@example.com");
        PaymentIdempotencyCommand command = command(customer.getId(), "idem-key-1", "{\"amount\":10}");

        PaymentIdempotencyDecision firstDecision = service.start(command);
        service.complete(firstDecision.recordId(), 201, "{\"paymentId\":\"pay_1\"}");

        PaymentIdempotencyDecision replayDecision = service.start(command);

        assertThat(firstDecision.type()).isEqualTo(PaymentIdempotencyDecisionType.STARTED);
        assertThat(firstDecision.shouldProcess()).isTrue();
        assertThat(replayDecision.type()).isEqualTo(PaymentIdempotencyDecisionType.REPLAY);
        assertThat(replayDecision.recordId()).isEqualTo(firstDecision.recordId());
        assertThat(replayDecision.responseStatus()).isEqualTo(201);
        assertThat(replayDecision.responseBody()).isEqualTo("{\"paymentId\":\"pay_1\"}");

        List<PaymentIdempotencyRecord> records = repository.findAll();
        assertThat(records)
                .singleElement()
                .satisfies(record -> {
                    assertThat(record.getRequestHash()).hasSize(64);
                    assertThat(record.getRequestHash()).isNotEqualTo(command.requestBody());
                });
    }

    @Test
    void completedRecordDoesNotOverwriteOriginalResponse() {
        UserModel customer = customer("stable-response-customer@example.com");
        PaymentIdempotencyCommand command = command(customer.getId(), "idem-key-stable", "{\"amount\":10}");
        PaymentIdempotencyDecision firstDecision = service.start(command);

        service.complete(firstDecision.recordId(), 201, "{\"paymentId\":\"pay_1\"}");
        service.complete(firstDecision.recordId(), 500, "{\"error\":\"late-overwrite\"}");

        PaymentIdempotencyDecision replayDecision = service.start(command);

        assertThat(replayDecision.type()).isEqualTo(PaymentIdempotencyDecisionType.REPLAY);
        assertThat(replayDecision.responseStatus()).isEqualTo(201);
        assertThat(replayDecision.responseBody()).isEqualTo("{\"paymentId\":\"pay_1\"}");
    }

    @Test
    void sameScopedKeyWithDifferentBodyIsRejected() {
        UserModel customer = customer("conflict-customer@example.com");
        service.start(command(customer.getId(), "idem-key-2", "{\"amount\":10}"));

        assertThatThrownBy(() -> service.start(command(customer.getId(), "idem-key-2", "{\"amount\":20}")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Idempotency key was reused with a different request body");
    }

    @Test
    void keyScopeIncludesCustomerEndpointAndOperation() {
        UserModel firstCustomer = customer("scope-a@example.com");
        UserModel secondCustomer = customer("scope-b@example.com");
        String idempotencyKey = "idem-key-3";
        String body = "{\"amount\":10}";

        PaymentIdempotencyDecision first = service.start(new PaymentIdempotencyCommand(
                firstCustomer.getId(),
                "/payments",
                "CREATE_PAYMENT",
                idempotencyKey,
                body
        ));
        PaymentIdempotencyDecision differentCustomer = service.start(new PaymentIdempotencyCommand(
                secondCustomer.getId(),
                "/payments",
                "CREATE_PAYMENT",
                idempotencyKey,
                body
        ));
        PaymentIdempotencyDecision differentEndpoint = service.start(new PaymentIdempotencyCommand(
                firstCustomer.getId(),
                "/refunds",
                "CREATE_PAYMENT",
                idempotencyKey,
                body
        ));
        PaymentIdempotencyDecision differentOperation = service.start(new PaymentIdempotencyCommand(
                firstCustomer.getId(),
                "/payments",
                "RETRY_PAYMENT",
                idempotencyKey,
                body
        ));

        assertThat(List.of(first, differentCustomer, differentEndpoint, differentOperation))
                .extracting(PaymentIdempotencyDecision::type)
                .containsOnly(PaymentIdempotencyDecisionType.STARTED);
        assertThat(repository.findAll()).hasSize(4);
    }

    @Test
    void concurrentDuplicateRequestsAllowOnlyOneProcessor() throws Exception {
        UserModel customer = customer("concurrent-customer@example.com");
        PaymentIdempotencyCommand command = command(customer.getId(), "idem-key-4", "{\"amount\":10}");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Callable<PaymentIdempotencyDecision> startAttempt = () -> {
            ready.countDown();
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent idempotency test did not start");
            }
            return service.start(command);
        };

        try {
            Future<PaymentIdempotencyDecision> firstAttempt = executor.submit(startAttempt);
            Future<PaymentIdempotencyDecision> secondAttempt = executor.submit(startAttempt);

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<PaymentIdempotencyDecision> results = List.of(
                    firstAttempt.get(5, TimeUnit.SECONDS),
                    secondAttempt.get(5, TimeUnit.SECONDS)
            );
            assertThat(results)
                    .extracting(PaymentIdempotencyDecision::type)
                    .containsExactlyInAnyOrder(
                            PaymentIdempotencyDecisionType.STARTED,
                            PaymentIdempotencyDecisionType.IN_PROGRESS
                    );
        } finally {
            executor.shutdownNow();
        }

        assertThat(repository.findAll()).hasSize(1);
    }

    private PaymentIdempotencyCommand command(long customerId, String idempotencyKey, String body) {
        return new PaymentIdempotencyCommand(
                customerId,
                "/payments",
                "CREATE_PAYMENT",
                idempotencyKey,
                body
        );
    }

    private UserModel customer(String username) {
        return userRepo.save(UserModel.builder()
                .username(username)
                .email(username)
                .firstName("Payment")
                .lastName("Customer")
                .build());
    }
}
