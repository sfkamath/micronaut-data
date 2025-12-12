package io.micronaut.data.model.geo;

import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.runtime.convert.geo.GeoJsonConverter;

@TypeDef(type = DataType.STRING, converter = GeoJsonConverter.class)
public record Point(double x, double y) implements GeoJson {
}
