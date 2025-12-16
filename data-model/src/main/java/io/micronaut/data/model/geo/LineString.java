package io.micronaut.data.model.geo;

import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.runtime.convert.geo.GeoJsonConverter;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;
import java.util.stream.Collectors;

@Serdeable
@TypeDef(type = DataType.STRING, converter = GeoJsonConverter.class)
public record LineString(List<Point> points) implements GeoJson {

    public LineString {
        if (CollectionUtils.isEmpty(points) || points.size() < 2) {
            throw new IllegalArgumentException("At least 2 points required for a LineString");
        }
    }

    public List<List<Double>> asCoords() {
        return points.stream()
            .map(Point::asCoords)
            .collect(Collectors.toList());
    }

    public static LineString fromCoords(List<List<Double>> coords) {
        if (CollectionUtils.isEmpty(coords)) {
            throw new IllegalArgumentException("Coordinates cannot be empty");
        }
        return new LineString(coords.stream().map(Point::fromCoords).toList());
    }
}
