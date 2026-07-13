package com.phu.ecommerceapi.reconciliation.application;

import java.util.Optional;

public interface ReconciliationRunLockPort {

    Optional<ReconciliationRunLock> tryAcquire();

    interface ReconciliationRunLock extends AutoCloseable {

        @Override
        void close();
    }
}
