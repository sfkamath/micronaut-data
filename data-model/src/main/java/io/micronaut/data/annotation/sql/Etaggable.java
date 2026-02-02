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
package io.micronaut.data.annotation.sql;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Type-level marker indicating that the entity is eligible for ETag generation.
 * <p>
 * When present, all simple, persisted properties are implicitly treated as if they had {@link ETagValue}, with
 * automatic exclusions applied for fields that cannot be included in the ETag (for example version fields,
 * relations, collections, and large binary/blob fields). A property explicitly annotated with {@link ETagValue}
 * still participates. Use property-level {@link ETagValue} to be explicit if needed.
 *
 * <p>Note: The exact set of excluded properties may evolve. At minimum, {@code @Version} is always excluded.</p>
 *
 * This annotation has effect only in conjunction with a {@link GeneratedEtag} property within the same entity.
 *
 * @since 5.0
 */
@Target(TYPE)
@Retention(RUNTIME)
public @interface Etaggable {
    /**
     * If true, include owning-side foreign-key association columns implicitly.
     * Collections and join-table relations remain excluded.
     */
    boolean includeForeignKeys() default false;
}
