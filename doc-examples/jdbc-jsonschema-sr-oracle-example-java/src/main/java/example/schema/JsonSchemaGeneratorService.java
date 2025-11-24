/*
 * Copyright 2017-$YEAR original authors
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
package example.schema;

import io.micronaut.json.JsonMapper;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.InputStream;

/**
 * Loads JSON Schemas generated at compile time by micronaut-json-schema processor.
 * Processor output is expected on classpath under META-INF/json-schema/<FQCN>.json
 */
@Singleton
public final class JsonSchemaGeneratorService {
    private static final String JSON_SCHEMA_LOCATION = "META-INF/json-schema/";

    private final JsonMapper jsonMapper;

    public JsonSchemaGeneratorService(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    /**
     * Locate and return the canonicalized JSON Schema (pretty/stable) as a String
     * for the given type. It reads the generated file and round-trips it via JsonMapper
     * to ensure stable formatting and ordering.
     * @param type The annotated type
     * @return Canonical JSON schema string
     * @throws IllegalStateException if the schema resource cannot be found or parsed
     */
    public String loadCanonicalSchema(Class<?> type) {
        // Try META-INF/json-schema/<FQCN>.json
        String resource = JSON_SCHEMA_LOCATION + type.getName() + ".json";
        byte[] bytes = readResource(resource);
        if (bytes != null) {
            return canonicalize(bytes);
        }
        // Fallback: some processors may output with '/' separators
        resource = JSON_SCHEMA_LOCATION + type.getName().replace('.', '/') + ".json";
        bytes = readResource(resource);
        if (bytes != null) {
            return canonicalize(bytes);
        }
        // Micronaut JSON Schema default output (observed): META-INF/schemas/<kebab-case-simple-name>.schema.json
        String kebab = toKebabCase(type.getSimpleName());
        resource = "META-INF/schemas/" + kebab + ".schema.json";
        bytes = readResource(resource);
        if (bytes != null) {
            return canonicalize(bytes);
        }
        throw new IllegalStateException("JSON Schema resource not found for " + type.getName() +
            " under META-INF/json-schema/ or META-INF/schemas/");
    }

    private static String toKebabCase(String simpleName) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < simpleName.length(); i++) {
            char ch = simpleName.charAt(i);
            if (Character.isUpperCase(ch)) {
                if (i > 0) {
                    sb.append('-');
                }
                sb.append(Character.toLowerCase(ch));
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    private byte[] readResource(String path) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                return null;
            }
            return is.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read schema resource: " + path, e);
        }
    }

    private String canonicalize(byte[] bytes) {
        try {
            Object tree = jsonMapper.readValue(bytes, Object.class);
            return jsonMapper.writeValueAsString(tree);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to canonicalize schema JSON", e);
        }
    }
}
