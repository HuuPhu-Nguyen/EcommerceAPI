package com.phu.ecommerceapi.reconciliation.infrastructure;

import com.phu.ecommerceapi.reconciliation.application.ReconciliationRunLockPort;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

@Component
public class PostgresReconciliationRunLockAdapter implements ReconciliationRunLockPort {

    private static final int LOCK_NAMESPACE = 17731;
    private static final int LOCK_KEY = 1380142926;

    private final DataSource dataSource;

    public PostgresReconciliationRunLockAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<ReconciliationRunLock> tryAcquire() {
        Connection connection = null;
        try {
            connection = dataSource.getConnection();
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(true);

            if (!tryAcquire(connection)) {
                connection.close();
                return Optional.empty();
            }

            return Optional.of(new PostgresReconciliationRunLock(connection, originalAutoCommit));
        } catch (SQLException exception) {
            closeQuietly(connection);
            throw new IllegalStateException("Could not acquire reconciliation run lock", exception);
        }
    }

    private boolean tryAcquire(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT pg_try_advisory_lock(?, ?)")) {
            statement.setInt(1, LOCK_NAMESPACE);
            statement.setInt(2, LOCK_KEY);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getBoolean(1);
            }
        }
    }

    private static void closeQuietly(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException ignored) {
        }
    }

    private static final class PostgresReconciliationRunLock implements ReconciliationRunLock {

        private final Connection connection;
        private final boolean originalAutoCommit;
        private boolean closed;

        private PostgresReconciliationRunLock(Connection connection, boolean originalAutoCommit) {
            this.connection = connection;
            this.originalAutoCommit = originalAutoCommit;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            try {
                unlock();
            } catch (SQLException exception) {
                throw new IllegalStateException("Could not release reconciliation run lock", exception);
            } finally {
                closeConnection();
            }
        }

        private void unlock() throws SQLException {
            try (PreparedStatement statement = connection.prepareStatement("SELECT pg_advisory_unlock(?, ?)")) {
                statement.setInt(1, LOCK_NAMESPACE);
                statement.setInt(2, LOCK_KEY);
                statement.execute();
            }
        }

        private void closeConnection() {
            try {
                connection.setAutoCommit(originalAutoCommit);
                connection.close();
            } catch (SQLException exception) {
                throw new IllegalStateException("Could not close reconciliation run lock connection", exception);
            }
        }
    }
}
