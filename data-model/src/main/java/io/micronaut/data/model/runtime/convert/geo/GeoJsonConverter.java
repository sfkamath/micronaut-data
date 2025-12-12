package io.micronaut.data.model.runtime.convert.geo;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.exceptions.ConversionErrorException;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.model.geo.GeoJson;
import io.micronaut.data.model.runtime.convert.AttributeConverter;
import io.micronaut.json.JsonMapper;
import jakarta.inject.Singleton;

import java.io.IOException;

@Singleton
public final class GeoJsonConverter implements AttributeConverter<GeoJson, String> {

    private final JsonMapper jsonMapper;

    GeoJsonConverter(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public String convertToPersistedValue(GeoJson entityValue, ConversionContext context) {
        if (entityValue == null) {
            return null;
        }
        try {
            return jsonMapper.writeValueAsString(entityValue);
        } catch (IOException e) {
            // TODO: Check which exception to throw
            throw new RuntimeException(e);
        }
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public @Nullable GeoJson convertToEntityValue(@Nullable String persistedValue, @NonNull ConversionContext context) {
        if (StringUtils.isEmpty(persistedValue)) {
            return null;
        }

        if (context instanceof ArgumentConversionContext argumentConvContext) {
            Argument<GeoJson> argument = argumentConvContext.getArgument();
            try {
                //Argument<GeoJson> argument = (Argument<GeoJson>) resolveArgument(context);
                return jsonMapper.readValue(persistedValue, argument);
            } catch (IOException e) {
                throw new ConversionErrorException(argument, e);
            }
        }

        // TODO: Check which exception to throw
        throw new RuntimeException("Unsupported conversation context: " + context);
    }
}
