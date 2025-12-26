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
package io.micronaut.transaction.recovery;

import io.micronaut.core.annotation.Internal;
import io.micronaut.transaction.TransactionStatus;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Resolves a definitive commit outcome for the most recent transaction
 * using a vendor-specific mechanism (for example, Oracle Transaction Guard).
 *
 * <p>Implementations are typically scoped per datasource (e.g. with a qualifier)
 * and can use the datasource to resolve the outcome on a new connection.</p>
 *
 * @since 5.0
 */
@Internal
public interface CommitOutcomeResolver {

    /**
     * Capture a token identifying the last transaction before commit is attempted.
     * Implementations should be invoked from a {@code beforeCompletion} hook while the
     * transaction is still active and can query the underlying connection/session.
     *
     * <p>Return {@code null} if capture is not supported or not applicable.</p>
     *
     * @param status The current transaction status (connection available via {@link TransactionStatus#getConnection()})
     * @return A vendor-specific token (e.g. Oracle LTXID hex string), or {@code null} if not available
     */
    @Nullable
    Object captureLtxid(@NonNull TransactionStatus<?> status);

    /**
     * Resolve the definitive commit outcome for the previously captured token.
     *
     * @param ltxidToken The token previously captured by {@link #captureLtxid(TransactionStatus)}
     * @return The commit outcome
     */
    @NonNull
    CommitOutcome resolve(@NonNull Object ltxidToken);
}
