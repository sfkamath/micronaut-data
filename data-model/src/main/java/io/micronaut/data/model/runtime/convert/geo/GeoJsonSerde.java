package io.micronaut.data.model.runtime.convert.geo;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.data.model.geo.GeoEntity;
import io.micronaut.data.model.geo.MultiPoint;
import io.micronaut.data.model.geo.Point;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serde;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.exceptions.SerdeException;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Singleton
final class GeoJsonSerde implements Serde<GeoJson> {

    private static final Map<String, Class<? extends GeoEntity>> GEO_TYPE_MAP = new HashMap<>();
    static {
        addGeoEntity(Point.class);
        //addGeoEntity(LineString.class);
        //addGeoEntity(Polygon.class);
        addGeoEntity(MultiPoint.class);
        //addGeoEntity(MultiLineString.class);
        //addGeoEntity(MultiPolygon.class);
    }

    private static void addGeoEntity(Class<? extends GeoEntity> geoEntityClass) {
        GEO_TYPE_MAP.put(geoEntityClass.getSimpleName().toLowerCase(), geoEntityClass);
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public @Nullable GeoJson deserialize(@NonNull Decoder decoder, @NonNull DecoderContext context, @NonNull Argument<? super GeoJson> type) throws IOException {
        Decoder objectDecoder = decoder.decodeObject();
        String typePropertyName = objectDecoder.decodeKey();
        if (!"type".equalsIgnoreCase(typePropertyName)) {
            throw new SerdeException("Expected 'type', found '" + typePropertyName + "' during GeoJson deserialization" );
        }
        String typePropertyValue = decoder.decodeString();
        Class<? extends GeoEntity> entityClass = GEO_TYPE_MAP.get(typePropertyValue.toLowerCase());
        if (entityClass == null) {
            throw new SerdeException("Unknown type: " + typePropertyValue);
        }

        String coordPropertyName = objectDecoder.decodeKey();
        if (!"coordinates".equalsIgnoreCase(coordPropertyName)) {
            throw new SerdeException("Expected 'coordinates', found '" + coordPropertyName + "' during GeoJson deserialization" );
        }

        Argument entityArg = Argument.of(entityClass);
        Deserializer deserializer = context.findDeserializer(entityClass);
        GeoEntity entity = (GeoEntity) deserializer.deserialize(objectDecoder, context, entityArg);

        objectDecoder.finishStructure();
        return new GeoJson(entity);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void serialize(@NonNull Encoder encoder, @NonNull EncoderContext context, @NonNull Argument<? extends GeoJson> type, @NonNull GeoJson value) throws IOException {
        GeoEntity entity = value.entity();
        Class<? extends GeoEntity> entityClass = entity.getClass();
        Argument<? extends GeoEntity> entityArg = Argument.of(entityClass);
        Encoder objectEncoder = encoder.encodeObject(Argument.OBJECT_ARGUMENT);
        objectEncoder.encodeKey("type");
        objectEncoder.encodeString(entityClass.getSimpleName());
        objectEncoder.encodeKey("coordinates");
        Serializer<GeoEntity> entitySerializer = (Serializer<GeoEntity>) context.findSerializer(entityArg);
        entitySerializer.serialize(objectEncoder, context, entityArg, entity);
        objectEncoder.finishStructure();
    }
}
