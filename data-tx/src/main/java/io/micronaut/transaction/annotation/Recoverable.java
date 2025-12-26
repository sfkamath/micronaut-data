/*
 * Copyright 2017-2025 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.transaction.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marks a transactional method as recoverable using database-provided
 * commit outcome semantics. Intended initially for Oracle Transaction Guard (TG)
 * to deterministically resolve ambiguous commit outcomes that may manifest as
 * {@code SQLRecoverableException} during commit acknowledgement loss.
 *
 * <p>Applies only to the synchronous (JDBC) transaction path in the initial version.</p>
 *
 * <p>Usage notes:
 * <ul>
 *   <li>Only effective on new top-level transactions (no nested/savepoint or participating tx).</li>
 *   <li>Business logic should be idempotent or otherwise safe to re-execute when outcome is NOT_COMMITTED.</li>
 *   <li>When the database reports COMMITTED, the original business result is returned and the exception is suppressed.</li>
 * </ul>
 * </p>
 *
 * @since 5.0
 */
@Documented
@Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
@Target({java.lang.annotation.ElementType.METHOD, java.lang.annotation.ElementType.TYPE})
public @interface Recoverable {

    /**
     * Exception types that should trigger recoverable handling.
     * Defaults to {@code java.sql.SQLRecoverableException}.
     *
     * @return The exception types that trigger recoverable handling.
     */
    Class<? extends Throwable>[] on() default {java.sql.SQLRecoverableException.class};

    /**
     * Maximum number of attempts beyond the initial try when the outcome
     * is NOT_COMMITTED or UNKNOWN (as per {@link #unknownOutcomePolicy()}).
     *
     * @return The maximum number of retry attempts.
     */
    int maxAttempts() default 1;

    /**
     * Backoff in milliseconds between retry attempts.
     *
     * @return The backoff in milliseconds between retry attempts.
     */
    long backoff() default 100L;

    /**
     * Policy to apply when the commit outcome cannot be determined.
     *
     * @return The policy to apply for unknown commit outcomes.
     */
    OutcomePolicy unknownOutcomePolicy() default OutcomePolicy.RETRY;

    /**
     * Outcome policy when the database returns an unknown commit outcome.
     */
    enum OutcomePolicy {
        /**
         * Retry the entire transactional method up to {@link #maxAttempts()} times.
         */
        RETRY,
        /**
         * Fail fast and rethrow the original exception.
         */
        FAIL
    }
}
