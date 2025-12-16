package io.micronaut.data.model.runtime.convert.geo;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.model.geo.GeoEntity;
import io.micronaut.data.model.runtime.convert.AttributeConverter;
import io.micronaut.json.JsonMapper;
import jakarta.inject.Singleton;

import java.io.IOException;

@Singleton
public final class GeoEntityConverter implements AttributeConverter<GeoEntity, String> {

    private final JsonMapper jsonMapper;

    GeoEntityConverter(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public String convertToPersistedValue(GeoEntity entityValue, ConversionContext context) {
        if (entityValue == null) {
            return null;
        }
        try {
            GeoJson geoJson = new GeoJson(entityValue);
            return jsonMapper.writeValueAsString(geoJson);
        } catch (IOException e) {
            // TODO: Check which exception to throw
            throw new RuntimeException(e);
        }
    }

    @Override
    public @Nullable GeoEntity convertToEntityValue(@Nullable String persistedValue, @NonNull ConversionContext context) {
        if (StringUtils.isEmpty(persistedValue)) {
            return null;
        }
        try {
            GeoJson geoJson = jsonMapper.readValue(persistedValue, GeoJson.class);
            return geoJson.entity();
        } catch (IOException e) {
            // TODO: Check which exception to throw
            throw new RuntimeException(e);
        }
    }
}
