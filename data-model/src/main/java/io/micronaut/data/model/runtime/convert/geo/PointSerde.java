package io.micronaut.data.model.runtime.convert.geo;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.data.model.geo.Point;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serde;
import jakarta.inject.Singleton;

import java.io.IOException;

@Singleton
final class PointSerde implements Serde<Point> {

    @Override
    public @Nullable Point deserialize(@NonNull Decoder decoder, @NonNull DecoderContext context, @NonNull Argument<? super Point> type) throws IOException {
        Decoder arrayDecoder = decoder.decodeArray();
        double x = arrayDecoder.decodeDouble();
        double y = arrayDecoder.decodeDouble();
        arrayDecoder.finishStructure();
        return new Point(x, y);
    }

    @Override
    public void serialize(@NonNull Encoder encoder, @NonNull EncoderContext context, @NonNull Argument<? extends Point> type, @NonNull Point value) throws IOException {
        Encoder arrayEncoder = encoder.encodeArray(Argument.DOUBLE);
        arrayEncoder.encodeDouble(value.x());
        arrayEncoder.encodeDouble(value.y());
        arrayEncoder.finishStructure();
    }
}
