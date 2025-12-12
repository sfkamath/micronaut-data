package io.micronaut.data.runtime.mapper.geo;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.data.model.geo.Point;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serde;
import io.micronaut.serde.exceptions.SerdeException;
import jakarta.inject.Singleton;

import java.io.IOException;

@Internal
@Singleton
public class PointSerde implements Serde<Point> {

    @Override
    public @Nullable Point deserialize(@NonNull Decoder decoder, @NonNull DecoderContext context, @NonNull Argument<? super Point> type) throws IOException {
        Decoder objectDecoder = decoder.decodeObject();
        String propertyName = objectDecoder.decodeKey();
        if (!"type".equalsIgnoreCase(propertyName) && !"coordinates".equalsIgnoreCase(propertyName)) {
            throw new SerdeException("Unknown property [" + propertyName + "] encountered during GeoJson deserialization, aaaa= " + type);
        }

        Point point;
        if ("type".equalsIgnoreCase(propertyName)) {
            validateType(objectDecoder, type);
            objectDecoder.decodeKey();
            point = deserializePoint(objectDecoder);
        } else {
            point = deserializePoint(objectDecoder);
            objectDecoder.decodeKey();
            validateType(objectDecoder, type);
        }
        objectDecoder.finishStructure();
        return point;
    }

    private void validateType(Decoder decoder, Argument<? super Point> type) throws IOException {
        String actualType = decoder.decodeString();
        String expectedType = type.getType().getSimpleName();
        if (!expectedType.equalsIgnoreCase(actualType)) {
            throw new SerdeException("Serialized type [" + actualType + "] doesn't match expected type [" + expectedType + "]");
        }
    }

    private Point deserializePoint(Decoder objectDecoder) throws IOException {
        Decoder arrayDecoder = objectDecoder.decodeArray();
        //arrayDecoder.hasNextArrayValue();
        double x = arrayDecoder.decodeDouble();
        double y = arrayDecoder.decodeDouble();
        arrayDecoder.finishStructure();
        return new Point(x, y);
    }

    @Override
    public void serialize(@NonNull Encoder encoder, @NonNull EncoderContext context, @NonNull Argument<? extends Point> type, @NonNull Point value) throws IOException {
        Encoder objectEncoder = encoder.encodeObject(Argument.OBJECT_ARGUMENT);
        objectEncoder.encodeKey("type");
        objectEncoder.encodeString(type.getType().getSimpleName());
        objectEncoder.encodeKey("coordinates");
        Encoder arrayEncoder = objectEncoder.encodeArray(Argument.OBJECT_ARGUMENT);
        arrayEncoder.encodeDouble(value.x());
        arrayEncoder.encodeDouble(value.y());
        arrayEncoder.finishStructure();
        objectEncoder.finishStructure();
    }
}
