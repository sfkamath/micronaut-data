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
package example;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.nitrite.annotation.NitriteRepository;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;

import java.util.List;

/**
 * Repository for spatial benchmark.
 */
@NitriteRepository
public interface SpatialLocationRepository extends CrudRepository<SpatialLocation, String> {

    List<SpatialLocation> findByName(String name);

    @Query("{\"location\": {\"$near\": {\"center\": :center, \"distance\": :distance}}}")
    List<SpatialLocation> findByLocationNear(Point center, double distance);

    @Query("{\"location\": {\"$within\": :area}}")
    List<SpatialLocation> findByLocationWithin(Geometry area);

    @Query("{\"location\": {\"$intersects\": :geometry}}")
    List<SpatialLocation> findByLocationIntersects(Geometry geometry);
}
