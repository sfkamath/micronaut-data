package io.micronaut.data.tck.tests

import io.micronaut.context.ApplicationContext
import io.micronaut.data.model.geo.LineString
import io.micronaut.data.model.geo.MultiLineString
import io.micronaut.data.model.geo.MultiPoint
import io.micronaut.data.model.geo.MultiPolygon
import io.micronaut.data.model.geo.Point
import io.micronaut.data.model.geo.Polygon
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

    void "test saving, reading and updating an entity with Point type"() {
        given:
        GeoEntity entity = new GeoEntity()
        entity.setPoint(new Point(2.0, 2.5))

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

    void "test saving, reading and updating an entity with MultiPoint type"() {
        given:
        GeoEntity entity = new GeoEntity()
        entity.setMultiPoint(new MultiPoint([
                new Point(1.1, 2.1),
                new Point(3.1, 4.1)
        ]))

        when:
        GeoEntity savedEntity = getGeoEntityRepository().save(entity)

        then:
        savedEntity.id > 0

        when:
        Optional<GeoEntity> foundEntity = getGeoEntityRepository().findById(savedEntity.id)

        then:
        foundEntity.isPresent()
        with (foundEntity.get().getMultiPoint()) {
            it.points()
            it.points().size() == 2
            it.points().get(0).x() == 1.1d
            it.points().get(0).y() == 2.1d
            it.points().get(1).x() == 3.1d
            it.points().get(1).y() == 4.1d
        }

        when:
        entity.setMultiPoint(new MultiPoint([
                new Point(5.1, 6.1),
                new Point(7.1, 8.1)
        ]))
        getGeoEntityRepository().update(entity)
        foundEntity = getGeoEntityRepository().findById(savedEntity.id)

        then:
        foundEntity.isPresent()
        with (foundEntity.get().getMultiPoint()) {
            it.points()
            it.points().size() == 2
            it.points().get(0).x() == 5.1d
            it.points().get(0).y() == 6.1d
            it.points().get(1).x() == 7.1d
            it.points().get(1).y() == 8.1d
        }
    }

    void "test saving, reading and updating an entity with LineString type"() {
        given:
        GeoEntity entity = new GeoEntity()
        entity.setLineString(new LineString([
                new Point(1.1, 2.1),
                new Point(3.1, 4.1)
        ]))

        when:
        GeoEntity savedEntity = getGeoEntityRepository().save(entity)

        then:
        savedEntity.id > 0

        when:
        Optional<GeoEntity> foundEntity = getGeoEntityRepository().findById(savedEntity.id)

        then:
        foundEntity.isPresent()
        with (foundEntity.get().getLineString()) {
            it.points()
            it.points().size() == 2
            it.points().get(0).x() == 1.1d
            it.points().get(0).y() == 2.1d
            it.points().get(1).x() == 3.1d
            it.points().get(1).y() == 4.1d
        }

        when:
        entity.setLineString(new LineString([
                new Point(5.1, 6.1),
                new Point(7.1, 8.1)
        ]))
        getGeoEntityRepository().update(entity)
        foundEntity = getGeoEntityRepository().findById(savedEntity.id)

        then:
        foundEntity.isPresent()
        with (foundEntity.get().getLineString()) {
            it.points()
            it.points().size() == 2
            it.points().get(0).x() == 5.1d
            it.points().get(0).y() == 6.1d
            it.points().get(1).x() == 7.1d
            it.points().get(1).y() == 8.1d
        }
    }

    void "test saving, reading and updating an entity with MultiLineString type"() {
        given:
        GeoEntity entity = new GeoEntity()
        entity.setMultiLineString(new MultiLineString([
                new LineString([
                        new Point(1.1, 1.2),
                        new Point(1.3, 1.4)
                ]),
                new LineString([
                        new Point(2.1, 2.2),
                        new Point(2.3, 2.4)
                ])
        ]))

        when:
        GeoEntity savedEntity = getGeoEntityRepository().save(entity)

        then:
        savedEntity.id > 0

        when:
        Optional<GeoEntity> foundEntity = getGeoEntityRepository().findById(savedEntity.id)

        then:
        foundEntity.isPresent()
        with (foundEntity.get().getMultiLineString()) {
            it.lineStrings()
            it.lineStrings().size() == 2
            it.lineStrings().get(0).points().size() == 2
            it.lineStrings().get(0).points().get(0).x() == 1.1d
            it.lineStrings().get(0).points().get(0).y() == 1.2d
            it.lineStrings().get(0).points().get(1).x() == 1.3d
            it.lineStrings().get(0).points().get(1).y() == 1.4d
            it.lineStrings().get(1).points().size() == 2
            it.lineStrings().get(1).points().get(0).x() == 2.1d
            it.lineStrings().get(1).points().get(0).y() == 2.2d
            it.lineStrings().get(1).points().get(1).x() == 2.3d
            it.lineStrings().get(1).points().get(1).y() == 2.4d
        }

        when:
        entity.setMultiLineString(new MultiLineString([
                new LineString([
                        new Point(3.1, 3.2),
                        new Point(3.3, 3.4)
                ]),
                new LineString([
                        new Point(4.1, 4.2),
                        new Point(4.3, 4.4)
                ])
        ]))
        getGeoEntityRepository().update(entity)
        foundEntity = getGeoEntityRepository().findById(savedEntity.id)

        then:
        foundEntity.isPresent()
        with (foundEntity.get().getMultiLineString()) {
            it.lineStrings()
            it.lineStrings().size() == 2
            it.lineStrings().get(0).points().size() == 2
            it.lineStrings().get(0).points().get(0).x() == 3.1d
            it.lineStrings().get(0).points().get(0).y() == 3.2d
            it.lineStrings().get(0).points().get(1).x() == 3.3d
            it.lineStrings().get(0).points().get(1).y() == 3.4d
            it.lineStrings().get(1).points().size() == 2
            it.lineStrings().get(1).points().get(0).x() == 4.1d
            it.lineStrings().get(1).points().get(0).y() == 4.2d
            it.lineStrings().get(1).points().get(1).x() == 4.3d
            it.lineStrings().get(1).points().get(1).y() == 4.4d
        }
    }

    void "test saving, reading and updating an entity with Polygon type"() {
        given:
        GeoEntity entity = new GeoEntity()
        entity.setPolygon(new Polygon([
                new LineString([
                        new Point(1.0, 1.0),
                        new Point(5.0, 1.0),
                        new Point(5.0, 2.5),
                        new Point(1.0, 2.5),
                        new Point(1.0, 1.0)
                ]),
                new LineString([
                        new Point(1.5, 1.5),
                        new Point(2.5, 1.5),
                        new Point(2.5, 2.0),
                        new Point(1.5, 1.5)
                ])
        ]))

        when:
        GeoEntity savedEntity = getGeoEntityRepository().save(entity)

        then:
        savedEntity.id > 0

        when:
        Optional<GeoEntity> foundEntity = getGeoEntityRepository().findById(savedEntity.id)

        then:
        foundEntity.isPresent()
        with (foundEntity.get().getPolygon().lineStrings()) {
            it.size() == 2
            it.get(0).points().size() == 5
            it.get(0).points().get(0).x() == 1.0d
            it.get(0).points().get(0).y() == 1.0d
            it.get(0).points().get(1).x() == 5.0d
            it.get(0).points().get(1).y() == 1.0d
            it.get(0).points().get(2).x() == 5.0d
            it.get(0).points().get(2).y() == 2.5d
            it.get(0).points().get(3).x() == 1.0d
            it.get(0).points().get(3).y() == 2.5d
            it.get(0).points().get(4).x() == 1.0d
            it.get(0).points().get(4).y() == 1.0d
            it.get(1).points().size() == 4
            it.get(1).points().get(0).x() == 1.5d
            it.get(1).points().get(0).y() == 1.5d
            it.get(1).points().get(1).x() == 2.5d
            it.get(1).points().get(1).y() == 1.5d
            it.get(1).points().get(2).x() == 2.5d
            it.get(1).points().get(2).y() == 2.0d
            it.get(1).points().get(3).x() == 1.5d
            it.get(1).points().get(3).y() == 1.5d
        }

        when:
        entity.setPolygon(new Polygon([
                new LineString([
                        new Point(1.0, 1.0),
                        new Point(5.0, 1.0),
                        new Point(5.0, 2.5),
                        new Point(1.0, 2.5),
                        new Point(1.0, 1.0)
                ])
        ]))
        getGeoEntityRepository().update(entity)
        foundEntity = getGeoEntityRepository().findById(savedEntity.id)

        then:
        foundEntity.isPresent()
        with (foundEntity.get().getPolygon().lineStrings()) {
            it.size() == 1
            it.get(0).points().size() == 5
            it.get(0).points().get(0).x() == 1.0d
            it.get(0).points().get(0).y() == 1.0d
            it.get(0).points().get(1).x() == 5.0d
            it.get(0).points().get(1).y() == 1.0d
            it.get(0).points().get(2).x() == 5.0d
            it.get(0).points().get(2).y() == 2.5d
            it.get(0).points().get(3).x() == 1.0d
            it.get(0).points().get(3).y() == 2.5d
            it.get(0).points().get(4).x() == 1.0d
            it.get(0).points().get(4).y() == 1.0d
        }
    }

    void "test saving, reading and updating an entity with MultiPolygon type"() {
        given:
        GeoEntity entity = new GeoEntity()
        entity.setMultiPolygon(new MultiPolygon([
                new Polygon([
                        new LineString([
                                new Point(1.0, 1.0),
                                new Point(5.0, 1.0),
                                new Point(5.0, 2.5),
                                new Point(1.0, 2.5),
                                new Point(1.0, 1.0)
                        ]),
                        new LineString([
                                new Point(1.5, 1.5),
                                new Point(2.5, 1.5),
                                new Point(2.5, 2.0),
                                new Point(1.5, 1.5)
                        ])
                ]),
                new Polygon([
                        new LineString([
                                new Point(11.0, 11.0),
                                new Point(15.0, 11.0),
                                new Point(15.0, 12.5),
                                new Point(11.0, 12.5),
                                new Point(11.0, 11.0)
                        ]),
                        new LineString([
                                new Point(11.5, 11.5),
                                new Point(12.5, 11.5),
                                new Point(12.5, 12.0),
                                new Point(11.5, 11.5)
                        ])
                ])
        ]))

        when:
        GeoEntity savedEntity = getGeoEntityRepository().save(entity)

        then:
        savedEntity.id > 0

        when:
        Optional<GeoEntity> foundEntity = getGeoEntityRepository().findById(savedEntity.id)

        then:
        foundEntity.isPresent()
        with(foundEntity.get().getMultiPolygon().polygons()) {
            it.size() == 2
            with(it.get(0)) {
                with(it.lineStrings()) {
                    with(it.get(0).points()) {
                        it.size() == 5
                        it.get(0).x() == 1.0d
                        it.get(0).y() == 1.0d
                        it.get(1).x() == 5.0d
                        it.get(1).y() == 1.0d
                        it.get(2).x() == 5.0d
                        it.get(2).y() == 2.5d
                        it.get(3).x() == 1.0d
                        it.get(3).y() == 2.5d
                        it.get(4).x() == 1.0d
                        it.get(4).y() == 1.0d
                    }
                    with(it.get(1).points()) {
                        it.size() == 4
                        it.get(0).x() == 1.5d
                        it.get(0).y() == 1.5d
                        it.get(1).x() == 2.5d
                        it.get(1).y() == 1.5d
                        it.get(2).x() == 2.5d
                        it.get(2).y() == 2.0d
                        it.get(3).x() == 1.5d
                        it.get(3).y() == 1.5d
                    }
                }
            }
            with(it.get(1)) {
                with(it.lineStrings()) {
                    with(it.get(0).points()) {
                        it.size() == 5
                        it.get(0).x() == 11.0d
                        it.get(0).y() == 11.0d
                        it.get(1).x() == 15.0d
                        it.get(1).y() == 11.0d
                        it.get(2).x() == 15.0d
                        it.get(2).y() == 12.5d
                        it.get(3).x() == 11.0d
                        it.get(3).y() == 12.5d
                        it.get(4).x() == 11.0d
                        it.get(4).y() == 11.0d
                    }
                    with(it.get(1).points()) {
                        it.size() == 4
                        it.get(0).x() == 11.5d
                        it.get(0).y() == 11.5d
                        it.get(1).x() == 12.5d
                        it.get(1).y() == 11.5d
                        it.get(2).x() == 12.5d
                        it.get(2).y() == 12.0d
                        it.get(3).x() == 11.5d
                        it.get(3).y() == 11.5d
                    }
                }
            }
        }

        when:
        entity.setMultiPolygon(new MultiPolygon([
                new Polygon([
                        new LineString([
                                new Point(1.0, 1.0),
                                new Point(5.0, 1.0),
                                new Point(5.0, 2.5),
                                new Point(1.0, 2.5),
                                new Point(1.0, 1.0)
                        ])
                ]),
                new Polygon([
                        new LineString([
                                new Point(11.0, 11.0),
                                new Point(15.0, 11.0),
                                new Point(15.0, 12.5),
                                new Point(11.0, 12.5),
                                new Point(11.0, 11.0)
                        ])
                ])
        ]))
        getGeoEntityRepository().update(entity)
        foundEntity = getGeoEntityRepository().findById(savedEntity.id)

        then:
        foundEntity.isPresent()
        with(foundEntity.get().getMultiPolygon().polygons()) {
            it.size() == 2
            with(it.get(0)) {
                with(it.lineStrings()) {
                    with(it.get(0).points()) {
                        it.size() == 5
                        it.get(0).x() == 1.0d
                        it.get(0).y() == 1.0d
                        it.get(1).x() == 5.0d
                        it.get(1).y() == 1.0d
                        it.get(2).x() == 5.0d
                        it.get(2).y() == 2.5d
                        it.get(3).x() == 1.0d
                        it.get(3).y() == 2.5d
                        it.get(4).x() == 1.0d
                        it.get(4).y() == 1.0d
                    }
                }
            }
            with(it.get(1)) {
                with(it.lineStrings()) {
                    with(it.get(0).points()) {
                        it.size() == 5
                        it.get(0).x() == 11.0d
                        it.get(0).y() == 11.0d
                        it.get(1).x() == 15.0d
                        it.get(1).y() == 11.0d
                        it.get(2).x() == 15.0d
                        it.get(2).y() == 12.5d
                        it.get(3).x() == 11.0d
                        it.get(3).y() == 12.5d
                        it.get(4).x() == 11.0d
                        it.get(4).y() == 11.0d
                    }
                }
            }
        }
    }
}
