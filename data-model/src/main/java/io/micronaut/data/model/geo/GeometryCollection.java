package io.micronaut.data.model.geo;

import java.util.List;

public record GeometryCollection(List<Geometry> geometries) implements Geometry {
}
