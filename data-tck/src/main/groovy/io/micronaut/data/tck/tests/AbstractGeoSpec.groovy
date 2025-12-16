package io.micronaut.data.tck.tests

import io.micronaut.context.ApplicationContext
import io.micronaut.data.model.geo.MultiPoint
import io.micronaut.data.model.geo.Point
import io.micronaut.data.tck.jdbc.entities.GeoEntity
import io.micronaut.data.tck.repositories.GeoEntityRepository
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

abstract class AbstractGeoSpec extends Specification {

    abstract GeoEntityRepository getGeoEntityRepository()

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties)

    void "test saving, reading and updating an entity with geospatial data"() {
        given:
        GeoEntity entity = new GeoEntity()
        Point point = new Point(2.0, 2.5)
        MultiPoint multiPoint = new MultiPoint([new Point(1.5, 2.5), new Point(2.5, 3.5)])
        entity.setPoint(point)
        entity.setMultiPoint(multiPoint)

        when:
        GeoEntity savedEntity = getGeoEntityRepository().save(entity)

        then:
        savedEntity.id > 0

        when:
        Optional<GeoEntity> foundEntity = getGeoEntityRepository().findById(savedEntity.id)

        then:
        foundEntity.isPresent()
        foundEntity.get().getPoint().x() == 2.0d
        foundEntity.get().getPoint().y() == 2.5d

        when:
        entity.setPoint(new Point(3.0, 3.5))
        getGeoEntityRepository().update(entity)
        foundEntity = getGeoEntityRepository().findById(savedEntity.id)

        then:
        foundEntity.isPresent()
        foundEntity.get().getPoint().x() == 3.0d
        foundEntity.get().getPoint().y() == 3.5d
    }
}
