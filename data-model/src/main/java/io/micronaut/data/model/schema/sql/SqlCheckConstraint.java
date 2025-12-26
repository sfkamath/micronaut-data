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
package io.micronaut.data.model.schema.sql;

import io.micronaut.core.annotation.Internal;

/**
 * Definition of a table-level CHECK constraint derived from entity metadata.
 *
 * This model is intentionally minimal and keeps the predicate in structured form
 * so that SQL builders can render identifiers with dialect-specific quoting.
 *
 * @param name       The constraint name (unescaped)
 * @param column     The column name (unescaped)
 * @param operator   The comparison operator (e.g. ">=", "<", "=")
 * @param value      The numeric literal value to compare with (as string, unquoted)
 * @param nullGuard  If true, the constraint must allow NULLs via (col IS NULL OR (...))
 *
 * @author radovanradic
 * @since 4.13.0
 */
@Internal
public record SqlCheckConstraint(
    String name,
    String column,
    String operator,
    String value,
    boolean nullGuard
) {
}
