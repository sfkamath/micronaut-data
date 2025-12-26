/*
 * Copyright 2017-2025 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package io.micronaut.transaction.recoverable;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.data.connection.ConnectionDefinition;
import io.micronaut.data.connection.ConnectionStatus;
import io.micronaut.transaction.TransactionCallback;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.TransactionOperations;
import io.micronaut.transaction.TransactionStatus;
import io.micronaut.transaction.annotation.Recoverable;
import io.micronaut.transaction.annotation.Transactional;
import io.micronaut.transaction.recovery.CommitOutcome;
import io.micronaut.transaction.recovery.CommitOutcomeResolver;
import io.micronaut.transaction.support.TransactionSynchronization;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.sql.SQLRecoverableException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

class RecoverableSpec {

    @Test
    void testRecoverableCommittedOutcomeReturnsBusinessResult() {
        try (ApplicationContext ctx = ApplicationContext.run()) {
            TestService svc = ctx.getBean(TestService.class);
            String v = svc.work();
            Assertions.assertEquals("ok-1", v, "Should return business result when TG reports COMMITTED");
            // ensure our fake tx ops executed once and threw recoverable exception
            FakeTxOps tx = ctx.getBean(FakeTxOps.class);
            Assertions.assertEquals(1, tx.attempts.get());
        }
    }

    // A simple service with a synchronous @Transactional method that is @Recoverable
    @Singleton
    static class TestService {
        private final AtomicInteger counter = new AtomicInteger();

        @Transactional
        @Recoverable(maxAttempts = 1, unknownOutcomePolicy = Recoverable.OutcomePolicy.RETRY)
        String work() {
            return "ok-" + counter.incrementAndGet();
        }
    }

    // Provide a CommitOutcomeResolver that always returns COMMITTED and ignores the token/connection
    @Singleton
    static class FakeResolver implements CommitOutcomeResolver {
        @Override
        public Object captureLtxid(TransactionStatus<?> status) {
            // token could be anything; the interceptor just passes it back to resolve
            return "TOKEN";
        }

        @Override
        public CommitOutcome resolve(Object ltxidToken) {
            return CommitOutcome.COMMITTED;
        }
    }

    // Provide a synchronous TransactionOperations bean that:
    // - Executes the callback with a status that captures synchronizations
    // - Invokes beforeCompletion (so resolver can capture token)
    // - Throws SQLRecoverableException after callback to simulate ambiguous commit ack loss (first attempt only)
    @Singleton
    static class FakeTxOps implements TransactionOperations<String> {
        final AtomicInteger attempts = new AtomicInteger();

        @Override
        public String getConnection() {
            return "FAKE_CONN";
        }

        @Override
        public boolean hasConnection() {
            return true;
        }

        @Override
        public Optional<TransactionStatus<String>> findTransactionStatus() {
            return Optional.empty();
        }

        @Override
        public boolean managesTransaction(TransactionStatus<String> transactionStatus) {
            return true;
        }

        @Override
        public <R> R execute(TransactionDefinition definition, TransactionCallback<String, R> callback) {
            List<TransactionSynchronization> syncs = new ArrayList<>(2);
            // minimal transaction status implementation
            TransactionStatus<String> status = new TransactionStatus<>() {
                private boolean rollbackOnly;
                @Override
                public Object getTransaction() {
                    return null;
                }

                @Override
                public String getConnection() {
                    return "FAKE_CONN";
                }

                @Override
                public ConnectionStatus<String> getConnectionStatus() {
                    return new ConnectionStatus<>() {
                        @Override
                        public boolean isNew() {
                            return true;
                        }

                        @Override
                        public String getConnection() {
                            return "FAKE_CONN";
                        }

                        @Override
                        public ConnectionDefinition getDefinition() {
                            return ConnectionDefinition.DEFAULT;
                        }

                        @Override
                        public void registerSynchronization(io.micronaut.data.connection.ConnectionSynchronization synchronization) {
                            // not used in this test
                        }
                    };
                }

                @Override
                public void registerSynchronization(TransactionSynchronization synchronization) {
                    syncs.add(synchronization);
                }

                @Override
                public boolean isNewTransaction() {
                    return true;
                }

                @Override
                public void setRollbackOnly() {
                    rollbackOnly = true;
                }

                @Override
                public boolean isRollbackOnly() {
                    return rollbackOnly;
                }

                @Override
                public boolean isCompleted() {
                    return false;
                }

                @Override
                public TransactionDefinition getTransactionDefinition() {
                    return definition;
                }
            };

            R result = callback.apply(status);
            // simulate commit lifecycle: call beforeCompletion so resolver can capture token
            for (TransactionSynchronization s : syncs) {
                s.beforeCompletion();
            }
            // then throw a recoverable exception only on first attempt
            if (attempts.incrementAndGet() == 1) {
                throw new RuntimeException(new SQLRecoverableException("simulated commit ack lost"));
            }
            return result;
        }
    }
}
