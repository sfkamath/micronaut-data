/*
 * Copyright 2017-2025 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package io.micronaut.data.processor.sql

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Version
import io.micronaut.data.annotation.sql.ColumnTransformer
import io.micronaut.data.annotation.DataTransformer
import io.micronaut.data.annotation.sql.GeneratedEtag
import io.micronaut.data.annotation.sql.ETagValue
import io.micronaut.data.model.PersistentEntity
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.Relation
import io.micronaut.data.annotation.sql.Etaggable
import io.micronaut.data.processor.visitors.AbstractDataSpec
import io.micronaut.data.runtime.criteria.RuntimeCriteriaBuilder

class VersionETagSpec extends AbstractDataSpec {

    def builder = new RuntimeCriteriaBuilder()
    def queryBuilder = new SqlQueryBuilder()

    void "version property uses read ColumnTransformer in WHERE"() {
        when:
        // Build an UPDATE ... WHERE etag = ? query using local entity class
        def query = builder.createCriteriaUpdate(ETagBook)
        def root = query.from(ETagBook)
        def sql = query
                .set("title", builder.parameter(String))
                .where(builder.equal(root.get("etag"), builder.parameter(String)))
                .build(queryBuilder)
                .query
        def entity = PersistentEntity.of(ETagBook)
        def etag = entity.getPropertyByName("etag")

        then:
        // Expect the WHERE left-hand side to use the read transformer and alias replacement:
        // Note: UPDATE uses no table alias by default, so @. resolves to empty prefix -> properties without alias
        sql == 'UPDATE "book" SET "title"=? WHERE (SYS_ROW_ETAG(id, title) = ?)'

        etag
        etag.annotationMetadata.hasAnnotation(Version)
        etag.annotationMetadata.hasAnnotation(GeneratedValue)
        etag.annotationMetadata.stringValue(ColumnTransformer, "read").get() == 'SYS_ROW_ETAG(@.id, @.title)'
    }

    void "test @GeneratedEtag with @Version field in the entity"() {
        when:
        buildEntity('test.MyEntity', '''
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.sql.GeneratedEtag;

@MappedEntity
record MyEntity(@Id @GeneratedValue Long id,
    String name,
    @Version Long version,
    @GeneratedEtag(function = "custom") String eTag) {}
''')
        then:
        def ex = thrown(RuntimeException)
        ex.message.contains("Entity with @Version field cannot have @GeneratedEtag field")
    }

    void "test @GeneratedEtag without @ETagValue fields in the entity"() {
        when:
        buildEntity('test.MyEntity', '''
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.sql.GeneratedEtag;

@MappedEntity
record MyEntity(@Id @GeneratedValue Long id,
    String name,
    Long version,
    @GeneratedEtag(function = "custom") String eTag) {}
''')
        then:
        def ex = thrown(RuntimeException)
        ex.message.contains("@GeneratedEtag requires at least one @ETagValue annotated field or @Etaggable on the entity")
    }

    void "implicit with includeForeignKeys adds FK column to function args"() {
        when:
        def entity = PersistentEntity.of(FkEntity)
        def etag = entity.getPropertyByName("etag")
        def readExpr = etag.annotationMetadata.stringValue(ColumnTransformer, "read")
            .orElseGet(() -> etag.annotationMetadata.stringValue(DataTransformer, "read").orElse(""))
        then:
        readExpr.contains("@.other_id")
    }
}

@MappedEntity
@Etaggable(includeForeignKeys = true)
class FkEntity {
    @Id
    @GeneratedValue
    Long id

    @Relation(Relation.Kind.MANY_TO_ONE)
    Other other

    @GeneratedEtag(function = "SYS_ROW_ETAG")
    String etag
}

@MappedEntity
class Other {
    @Id
    @GeneratedValue
    Long id
    String name
}

@MappedEntity("book")
class ETagBook {
    @ETagValue
    @Id
    Long id
    @ETagValue
    String title
    @GeneratedEtag(function = "SYS_ROW_ETAG")
    String etag
}
