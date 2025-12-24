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
package io.micronaut.data.processor.mappers.sql;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Version;
import io.micronaut.data.annotation.sql.ColumnTransformer;
import io.micronaut.data.annotation.sql.ETag;
import io.micronaut.inject.annotation.NamedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * Maps {@link ETag} to:
 * <ul>
 *     <li>{@link Version}</li>
 *     <li>{@link GeneratedValue}</li>
 *     <li>{@link ColumnTransformer} read expression {@code function(@.f1, @.f2, ...)}</li>
 * </ul>
 */
public final class ETagAnnotationMapper implements NamedAnnotationMapper {

    @Override
    public String getName() {
        return ETag.class.getName();
    }

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<Annotation> annotation, VisitorContext visitorContext) {
        List<AnnotationValue<?>> out = new ArrayList<>(3);

        // Always add @Version and @GeneratedValue
        out.add(AnnotationValue.builder(Version.class).build());
        out.add(AnnotationValue.builder(GeneratedValue.class).build());

        // Build <function>(@.field1, @.field2, ...)
        String function = annotation.stringValue("function").orElseThrow(() ->
            new IllegalArgumentException("@ETag requires 'function' value"));
        String[] fields = annotation.stringValues("fields");
        String expr = buildFunctionCall(function, fields);

        out.add(
            AnnotationValue.builder(ColumnTransformer.class)
                .member("read", expr)
                .build()
        );

        return out;
    }

    private static String buildFunctionCall(String function, String[] fields) {
        StringJoiner joiner = new StringJoiner(", ");
        if (fields != null) {
            for (String f : fields) {
                joiner.add("@." + f);
            }
        }
        return function + "(" + joiner + ")";
    }
}
