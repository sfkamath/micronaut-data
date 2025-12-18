package io.micronaut.data.model.runtime.convert.geo;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.serialize.exceptions.SerializationException;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.model.geo.Geometry;
import io.micronaut.data.model.geo.GeometryCollection;
import io.micronaut.data.model.geo.LineString;
import io.micronaut.data.model.geo.MultiLineString;
import io.micronaut.data.model.geo.MultiPoint;
import io.micronaut.data.model.geo.MultiPolygon;
import io.micronaut.data.model.geo.Point;
import io.micronaut.data.model.geo.Polygon;
import io.micronaut.data.model.runtime.convert.AttributeConverter;
import io.micronaut.json.JsonMapper;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Singleton
public final class GeometryConverter implements AttributeConverter<Geometry, String> {

    private final JsonMapper jsonMapper;

    GeometryConverter(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public String convertToPersistedValue(Geometry entityValue, ConversionContext context) {
        if (entityValue == null) {
            return null;
        }
        GeoJson geoJson = getGeoJson(entityValue);
        try {
            return jsonMapper.writeValueAsString(geoJson);
        } catch (IOException e) {
            throw new SerializationException("Failed to serialize GeoJson entity [" + geoJson + "]", e);
        }
    }

    @Override
    public @Nullable Geometry convertToEntityValue(@Nullable String persistedValue, @NonNull ConversionContext context) {
        if (StringUtils.isEmpty(persistedValue)) {
            return null;
        }
        GeoJson geoJson;
        try {
            geoJson = jsonMapper.readValue(persistedValue, GeoJson.class);
        } catch (IOException e) {
            throw new SerializationException("Failed to deserialize json [" + persistedValue + "]", e);
        }
        return getGeometry(geoJson);
    }

    private GeoJson getGeoJson(Geometry geometry) {
        if (geometry instanceof Point point) {
            return new PointGeoJson("Point", point.asCoords());
        }
        if (geometry instanceof MultiPoint multiPoint) {
            return new MultiPointGeoJson("MultiPoint", multiPoint.asCoords());
        }
        if (geometry instanceof LineString lineString) {
            return new LineStringGeoJson("LineString", lineString.asCoords());
        }
        if (geometry instanceof MultiLineString multiLineString) {
            return new MultiLineStringGeoJson("MultiLineString", multiLineString.asCoords());
        }
        if (geometry instanceof Polygon polygon) {
            return new PolygonGeoJson("Polygon", polygon.asCoords());
        }
        if (geometry instanceof MultiPolygon multiPolygon) {
            return new MultiPolygonGeoJson("MultiPolygon", multiPolygon.asCoords());
        }
        if (geometry instanceof GeometryCollection geometryCollection) {
            return getGeoJsonCollection(geometryCollection);
        }
        throw new IllegalArgumentException("Not supported geometry implementation: " + geometry.getClass());
    }

    private GeoJsonCollection getGeoJsonCollection(GeometryCollection geometryCollection) {
        List<GeoJson> geoJsons = new ArrayList<>();
        geometryCollection.geometries().forEach(geometry -> {
            if (geometry instanceof GeometryCollection nestedGeometryCollection) {
                geoJsons.add(getGeoJsonCollection(nestedGeometryCollection));
            } else {
                geoJsons.add(getGeoJson(geometry));
            }
        });
        return new GeoJsonCollection("GeometryCollection", geoJsons);
    }

    private Geometry getGeometry(GeoJson geoJson) {
        if (geoJson instanceof PointGeoJson pointGeoJson) {
            return Point.fromCoords(pointGeoJson.coordinates());
        }
        if (geoJson instanceof MultiPointGeoJson multiPointGeoJson) {
            return MultiPoint.fromCoords(multiPointGeoJson.coordinates());
        }
        if (geoJson instanceof LineStringGeoJson lineStringGeoJson) {
            return LineString.fromCoords(lineStringGeoJson.coordinates());
        }
        if (geoJson instanceof MultiLineStringGeoJson multiLineStringGeoJson) {
            return MultiLineString.fromCoords(multiLineStringGeoJson.coordinates());
        }
        if (geoJson instanceof PolygonGeoJson polygonGeoJson) {
            return Polygon.fromCoords(polygonGeoJson.coordinates());
        }
        if (geoJson instanceof MultiPolygonGeoJson multiPolygonGeoJson) {
            return MultiPolygon.fromCoords(multiPolygonGeoJson.coordinates());
        }
        if (geoJson instanceof GeoJsonCollection geoJsonCollection) {
            return getGeometryCollection(geoJsonCollection);
        }
        throw new IllegalArgumentException("Not supported GeoJson implementation: " + geoJson.getClass());
    }

    private Geometry getGeometryCollection(GeoJsonCollection geoJsonCollection) {
        List<Geometry> geometries = new ArrayList<>();
        geoJsonCollection.geometries.forEach(geoJson -> {
            if (geoJson instanceof GeoJsonCollection nestedGeoJsonCollection) {
                geometries.add(getGeometryCollection(nestedGeoJsonCollection));
            } else {
                geometries.add(getGeometry(geoJson));
            }
        });
        return new GeometryCollection(geometries);
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type", visible = true)
    @JsonSubTypes({
        @JsonSubTypes.Type(value = PointGeoJson.class, name = "Point"),
        @JsonSubTypes.Type(value = MultiPointGeoJson.class, name = "MultiPoint"),
        @JsonSubTypes.Type(value = LineStringGeoJson.class, name = "LineString"),
        @JsonSubTypes.Type(value = MultiLineStringGeoJson.class, name = "MultiLineString"),
        @JsonSubTypes.Type(value = PolygonGeoJson.class, name = "Polygon"),
        @JsonSubTypes.Type(value = MultiPolygonGeoJson.class, name = "MultiPolygon"),
        @JsonSubTypes.Type(value = GeoJsonCollection.class, name = "GeometryCollection")
    })
    sealed interface GeoJson permits PointGeoJson, MultiPointGeoJson, LineStringGeoJson,
        MultiLineStringGeoJson, PolygonGeoJson, GeoJsonCollection, MultiPolygonGeoJson {}

    @Serdeable
    record PointGeoJson(String type, List<Double> coordinates) implements GeoJson {}

    @Serdeable
    record MultiPointGeoJson(String type, List<List<Double>> coordinates) implements GeoJson {}

    @Serdeable
    record LineStringGeoJson(String type, List<List<Double>> coordinates) implements GeoJson {}

    @Serdeable
    record MultiLineStringGeoJson(String type, List<List<List<Double>>> coordinates) implements GeoJson {}

    @Serdeable
    record PolygonGeoJson(String type, List<List<List<Double>>> coordinates) implements GeoJson {}

    @Serdeable
    record MultiPolygonGeoJson(String type, List<List<List<List<Double>>>> coordinates) implements GeoJson {}

    @Serdeable
    record GeoJsonCollection(String type, List<GeoJson> geometries) implements GeoJson {}
}
