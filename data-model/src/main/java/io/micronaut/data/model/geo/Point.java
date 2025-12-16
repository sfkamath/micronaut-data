package io.micronaut.data.model.geo;

import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.runtime.convert.geo.GeoJsonConverter;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

@Serdeable
@TypeDef(type = DataType.STRING, converter = GeoJsonConverter.class)
public record Point(double x, double y) implements GeoJson {

    public List<Double> asCoords() {
        return List.of(x, y);
    }

    public static Point fromCoords(List<Double> coords) {
        if (CollectionUtils.isEmpty(coords)) {
            throw new IllegalArgumentException("Coordinates cannot be empty");
        }
        if (coords.size() != 2) {
            throw new IllegalArgumentException("Coordinates must have 2 elements");
        }
        return new Point(coords.get(0), coords.get(1));
    }
}
