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
public record Polygon(List<LineString> lineStrings) implements GeoJson {

    public Polygon {
        if (CollectionUtils.isEmpty(lineStrings)) {
            throw new IllegalArgumentException("Polygon must have at least one ring (outer boundary)");
        }
        LineString outerRing = lineStrings.getFirst();
        if (outerRing.points() == null || outerRing.points().size() < 4) {
            throw new IllegalArgumentException("Outer ring must have at least 4 points (closed and minimum size)");
        }
        Point first = outerRing.points().getFirst();
        Point last = outerRing.points().getLast();
        if (!first.equals(last)) {
            throw new IllegalArgumentException("Outer ring is not closed: first point does not equal last point");
        }
    }

    public List<List<List<Double>>> asCoords() {
        return lineStrings.stream()
            .map(LineString::asCoords)
            .collect(Collectors.toList());
    }

    public static MultiLineString fromCoords(List<List<List<Double>>> coords) {
        if (CollectionUtils.isEmpty(coords)) {
            throw new IllegalArgumentException("Coordinates cannot be empty");
        }
        return new MultiLineString(coords.stream().map(LineString::fromCoords).toList());
    }
}
