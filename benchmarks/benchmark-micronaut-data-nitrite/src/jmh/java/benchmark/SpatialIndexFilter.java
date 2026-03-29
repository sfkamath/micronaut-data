/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package benchmark;

import example.SpatialLocation;
import example.SpatialLocationRepository;
import io.micronaut.context.ApplicationContext;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Benchmark for spatial index filters in Nitrite.
 * Tests $near, $within, and $intersects operators with spatial indexing.
 * 
 * <p>This benchmark measures the performance of Nitrite's R-tree based spatial indexing
 * for geographic queries. The spatial module (nitrite-spatial) must be on the classpath.</p>
 */
@State(Scope.Benchmark)
public class SpatialIndexFilter {

    @Param({"100", "500", "1000"})
    public int datasetSize;

    ApplicationContext applicationContext;
    SpatialLocationRepository spatialRepository;
    GeometryFactory geometryFactory;

    // Test data
    List<SpatialLocation> testLocations;
    Point centerPoint;
    Polygon boundingBox;
    Geometry intersectingLine;

    @Setup
    public void prepare() throws Exception {
        geometryFactory = new GeometryFactory();

        Map<String, Object> props = new HashMap<>();
        props.put("nitrite.storage-mode", "IN_MEMORY");
        props.put("logger.levels.io.micronaut.data.query", "INFO");
        props.put("logger.levels.io.micronaut.data.nitrite", "INFO");

        applicationContext = ApplicationContext.run(props);
        spatialRepository = applicationContext.getBean(SpatialLocationRepository.class);

        // Generate test locations spread across a geographic area
        testLocations = new ArrayList<>();
        for (int i = 0; i < datasetSize; i++) {
            // Spread points across a 10x10 degree area (simulating a region)
            double lon = -100.0 + (i % 10) * 1.0;  // Longitude: -100 to -91
            double lat = 30.0 + (i / 10) * 1.0;    // Latitude: 30 to 39
            Point location = geometryFactory.createPoint(new Coordinate(lon, lat));
            testLocations.add(new SpatialLocation("Location_" + i, location));
        }

        // Save all locations (triggers spatial index creation)
        spatialRepository.saveAll(testLocations);

        // Setup query parameters
        centerPoint = geometryFactory.createPoint(new Coordinate(-95.0, 35.0));  // Center of our grid

        // Create bounding box (5x5 degree area)
        Coordinate[] coords = new Coordinate[] {
            new Coordinate(-97.0, 33.0),
            new Coordinate(-93.0, 33.0),
            new Coordinate(-93.0, 37.0),
            new Coordinate(-97.0, 37.0),
            new Coordinate(-97.0, 33.0)  // Close the ring
        };
        boundingBox = geometryFactory.createPolygon(coords);

        // Create line that intersects multiple points
        intersectingLine = geometryFactory.createLineString(new Coordinate[] {
            new Coordinate(-98.0, 34.0),
            new Coordinate(-92.0, 36.0)
        });
    }

    @TearDown
    public void cleanup() {
        if (applicationContext != null) {
            applicationContext.close();
        }
    }

    /**
     * Benchmark $near spatial filter with R-tree index.
     * Finds all locations within a specified distance of a center point.
     * Uses Nitrite's spatial index for efficient proximity search.
     */
    @Benchmark
    public void measureNearFilter() {
        // Search within 2 degree radius (approximately 220km at equator)
        List<SpatialLocation> results = spatialRepository.findByLocationNear(centerPoint, 2.0);
        // Results should be non-empty
        if (results.isEmpty()) {
            throw new RuntimeException("Expected non-empty results for near query");
        }
    }

    /**
     * Benchmark $within spatial filter with R-tree index.
     * Finds all locations within a bounding polygon.
     * Uses Nitrite's spatial index for efficient containment search.
     */
    @Benchmark
    public void measureWithinFilter() {
        List<SpatialLocation> results = spatialRepository.findByLocationWithin(boundingBox);
        // Results should be non-empty
        if (results.isEmpty()) {
            throw new RuntimeException("Expected non-empty results for within query");
        }
    }

    /**
     * Benchmark $intersects spatial filter with R-tree index.
     * Finds all locations that intersect with a line geometry.
     * Uses Nitrite's spatial index for efficient intersection search.
     */
    @Benchmark
    public void measureIntersectsFilter() {
        List<SpatialLocation> results = spatialRepository.findByLocationIntersects(intersectingLine);
        // Results should be non-empty
        if (results.isEmpty()) {
            throw new RuntimeException("Expected non-empty results for intersects query");
        }
    }

    /**
     * Benchmark combined spatial operations.
     * Exercises all three spatial filter types in sequence.
     */
    @Benchmark
    public void measureAllSpatialFilters() {
        // Near query
        spatialRepository.findByLocationNear(centerPoint, 2.0);

        // Within query
        spatialRepository.findByLocationWithin(boundingBox);

        // Intersects query
        spatialRepository.findByLocationIntersects(intersectingLine);
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(".*" + SpatialIndexFilter.class.getSimpleName() + ".*")
                .warmupIterations(2)
                .measurementIterations(3)
                .forks(1)
                .build();

        new Runner(opt).run();
    }
}
