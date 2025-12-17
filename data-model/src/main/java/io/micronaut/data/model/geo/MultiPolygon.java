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
public record MultiPolygon(List<Polygon> polygons) implements GeoJson {

    public List<List<List<List<Double>>>> asCoords() {
        return polygons.stream()
            .map(Polygon::asCoords)
            .collect(Collectors.toList());
    }

    public static MultiPolygon fromCoords(List<List<List<List<Double>>>> coords) {
        if (CollectionUtils.isEmpty(coords)) {
            throw new IllegalArgumentException("Coordinates cannot be empty");
        }
        return new MultiPolygon(coords.stream().map(Polygon::fromCoords).toList());
    }
}
