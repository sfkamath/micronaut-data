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

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Generic helper annotation to configure a computed ETag for optimistic locking.
 * <p>
 * This annotation is a convenience meta-mapping that:
 * <ul>
 *     <li>Marks the property as versioned and generated (equivalent to applying
 *     {@code @io.micronaut.data.annotation.Version} and {@code @io.micronaut.data.annotation.GeneratedValue}).</li>
 *     <li>Applies a {@link ColumnTransformer} read expression like
 *     {@code <function>(@.field1, @.field2, ...)} based on {@link #function()} and {@link ETagPart} annotated fields.</li>
 * </ul>
 * <p>
 * Example:
 * <pre>{@code
 * {@literal @}MappedEntity
 * class Book {
 *   {@literal @}Id
 *   {@literal @}GeneratedValue
 *   {@literal @}ETagPart
 *   Long id;
 *
 *   {@literal @}ETagPart
 *   String title;
 *
 *   {@literal @}ETag(function = "SYS_ROW_ETAG")
 *   String etag;
 * }
 * }</pre>
 *
 * The actual mapping to {@code @Version}, {@code @GeneratedValue} and {@code @ColumnTransformer} is performed
 * by the MappedEntityVisitor in the data-processor module during annotation processing.
 *
 * @author radovanradic
 * @since 5.0
 */
@Target({FIELD, METHOD})
@Retention(RUNTIME)
public @interface ETag {

    /**
     * The SQL function name to compute the ETag (e.g. {@code SYS_ROW_ETAG}).
     *
     * @return function name
     */
    String function();
}
