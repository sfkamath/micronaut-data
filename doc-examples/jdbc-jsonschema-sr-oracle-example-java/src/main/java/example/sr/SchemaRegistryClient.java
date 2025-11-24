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
package example.sr;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.HttpStatus;
import io.micronaut.core.annotation.NonNull;
import jakarta.inject.Singleton;

import java.util.Map;

/**
 * Minimal Confluent Schema Registry client for JSON Schema.
 */
@Singleton
public class SchemaRegistryClient {

    private final HttpClient client;
    private final String apiKey;
    private final String apiSecret;

    public SchemaRegistryClient(
        @Client("${schemaRegistry.url}") HttpClient client,
        @io.micronaut.context.annotation.Value("${schemaRegistry.key:}") String apiKey,
        @io.micronaut.context.annotation.Value("${schemaRegistry.secret:}") String apiSecret
    ) {
        this.client = client;
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
    }

    /**
     * Returns the latest subject version payload as string, or null if subject not found.
     */
    public String getLatest(@NonNull String subject) {
        var req = HttpRequest.GET("/subjects/" + subject + "/versions/latest");
        if (!apiKey.isEmpty()) {
            req = req.basicAuth(apiKey, apiSecret);
        }
        try {
            return client.toBlocking().retrieve(req);
        } catch (HttpClientResponseException e) {
            if (e.getStatus() == HttpStatus.NOT_FOUND) {
                return null;
            }
            throw e;
        }
    }

    /**
     * Sets subject-level compatibility to BACKWARD (optional convenience).
     */
    public void setBackwardCompatibility(@NonNull String subject) {
        var body = Map.of("compatibility", "BACKWARD");
        var req = HttpRequest.PUT("/config/" + subject, body)
            .contentType(MediaType.APPLICATION_JSON_TYPE);
        if (!apiKey.isEmpty()) {
            req = req.basicAuth(apiKey, apiSecret);
        }
        client.toBlocking().exchange(req);
    }

    /**
     * Registers a JSON Schema for the given subject.
     * Returns raw response (contains id).
     */
    @NonNull
    public String registerJson(@NonNull String subject, @NonNull String schemaJson) {
        var body = Map.of(
            "schemaType", "JSON",
            "schema", schemaJson
        );
        var req = HttpRequest.POST("/subjects/" + subject + "/versions", body)
            .contentType(MediaType.APPLICATION_JSON_TYPE);
        if (!apiKey.isEmpty()) {
            req = req.basicAuth(apiKey, apiSecret);
        }
        return client.toBlocking().retrieve(req);
    }
}
