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

/**
 * Definitive commit outcome as reported by a database guard facility
 * such as Oracle Transaction Guard.
 *
 * @since 5.0
 */
@Internal
public enum CommitOutcome {
    /**
     * The transaction committed successfully.
     */
    COMMITTED,
    /**
     * The transaction did not commit (rolled back or not applied).
     */
    NOT_COMMITTED,
    /**
     * The outcome could not be determined.
     */
    UNKNOWN
}
