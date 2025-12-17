package io.micronaut.data.model.runtime.convert.geo;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.serialize.exceptions.SerializationException;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.model.geo.GeoJson;
import io.micronaut.data.model.geo.LineString;
import io.micronaut.data.model.geo.MultiLineString;
import io.micronaut.data.model.geo.MultiPoint;
import io.micronaut.data.model.geo.Point;
import io.micronaut.data.model.geo.Polygon;
import io.micronaut.data.model.runtime.convert.AttributeConverter;
import io.micronaut.json.JsonMapper;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Singleton
@SuppressWarnings("rawtypes")
public final class GeoJsonConverter implements AttributeConverter<GeoJson, String> {

    private static final Map<String, Function<List, GeoJson>> FROM_COORDS_FUNC_MAP = new HashMap<>();

    static {
        FROM_COORDS_FUNC_MAP.put(getType(Point.class), Point::fromCoords);
        FROM_COORDS_FUNC_MAP.put(getType(MultiPoint.class), MultiPoint::fromCoords);
        FROM_COORDS_FUNC_MAP.put(getType(LineString.class), LineString::fromCoords);
        FROM_COORDS_FUNC_MAP.put(getType(MultiLineString.class), MultiLineString::fromCoords);
        FROM_COORDS_FUNC_MAP.put(getType(Polygon.class), Polygon::fromCoords);
    }

    private static final Map<String, Function<GeoJson, List>> AS_COORDS_FUNC_MAP = new HashMap<>();

    static {
        AS_COORDS_FUNC_MAP.put(getType(Point.class), entity -> ((Point) entity).asCoords());
        AS_COORDS_FUNC_MAP.put(getType(MultiPoint.class), entity -> ((MultiPoint) entity).asCoords());
        AS_COORDS_FUNC_MAP.put(getType(LineString.class), entity -> ((LineString) entity).asCoords());
        AS_COORDS_FUNC_MAP.put(getType(MultiLineString.class), entity -> ((MultiLineString) entity).asCoords());
        AS_COORDS_FUNC_MAP.put(getType(Polygon.class), entity -> ((Polygon) entity).asCoords());
    }

    private static String getType(Class<? extends GeoJson> entityClass) {
        return entityClass.getSimpleName().toLowerCase();
    }

    private final JsonMapper jsonMapper;

    GeoJsonConverter(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public String convertToPersistedValue(GeoJson entityValue, ConversionContext context) {
        if (entityValue == null) {
            return null;
        }
        String type = entityValue.getClass().getSimpleName();
        Function<GeoJson, List> coordsFunc = AS_COORDS_FUNC_MAP.get(type.toLowerCase());
        if (coordsFunc == null) {
            throw new IllegalStateException("There is no registered function for conversion of entity [" + type + "] to coordinates");
        }
        List<?> coords = coordsFunc.apply(entityValue);
        try {
            return jsonMapper.writeValueAsString(new GeoJsonWrapper(type, coords));
        } catch (IOException e) {
            throw new SerializationException("Failed to serialize entity [" + entityValue + "]", e);
        }
    }

    @Override
    public @Nullable GeoJson convertToEntityValue(@Nullable String persistedValue, @NonNull ConversionContext context) {
        if (StringUtils.isEmpty(persistedValue)) {
            return null;
        }
        GeoJsonWrapper geoJsonWrapper;
        try {
            geoJsonWrapper = jsonMapper.readValue(persistedValue, GeoJsonWrapper.class);
        } catch (IOException e) {
            throw new SerializationException("Failed to deserialize json [" + persistedValue + "]", e);
        }
        String type = geoJsonWrapper.type().toLowerCase();
        Function<List, GeoJson> entityFunc = FROM_COORDS_FUNC_MAP.get(type);
        if (entityFunc == null) {
            throw new IllegalStateException("There is no registered function for conversion of coordinates to entity [" + type + "]");
        }

        return entityFunc.apply(geoJsonWrapper.coordinates());
    }

    @Serdeable
    record GeoJsonWrapper(String type, List<?> coordinates) {
    }
}
