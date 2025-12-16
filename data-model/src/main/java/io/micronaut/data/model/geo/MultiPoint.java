package io.micronaut.data.model.geo;

import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.runtime.convert.geo.GeoEntityConverter;

@TypeDef(type = DataType.STRING, converter = GeoEntityConverter.class)
public record MultiPoint(Point... points) implements GeoEntity {
}
