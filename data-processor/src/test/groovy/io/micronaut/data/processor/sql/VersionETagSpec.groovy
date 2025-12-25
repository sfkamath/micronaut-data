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
import io.micronaut.data.annotation.sql.ETagValueBased
import io.micronaut.data.annotation.sql.ETagValue
import io.micronaut.data.model.PersistentEntity
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder
import io.micronaut.data.processor.model.SourcePersistentEntity
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

    void "test @ETagValueBased with @Version field in the entity"() {
        when:
        buildEntity('test.MyEntity', '''
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.sql.ETagValueBased;

@MappedEntity
record MyEntity(@Id @GeneratedValue Long id,
    String name,
    @Version Long version,
    @ETagValueBased(function = "custom") String eTag) {}
''')
        then:
        def ex = thrown(RuntimeException)
        ex.message.contains("Entity with @Version field cannot have @ETagValueBased field")
    }

    void "test @ETagValueBased without @ETagValue fields in the entity"() {
        when:
        buildEntity('test.MyEntity', '''
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.sql.ETagValueBased;

@MappedEntity
record MyEntity(@Id @GeneratedValue Long id,
    String name,
    Long version,
    @ETagValueBased(function = "custom") String eTag) {}
''')
        then:
        def ex = thrown(RuntimeException)
        ex.message.contains("@ETagValueBased requires at least one @ETagValue annotated field")
    }
}

import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Id

@MappedEntity("book")
class ETagBook {
    @ETagValue
    @Id
    Long id
    @ETagValue
    String title
    @ETagValueBased(function = "SYS_ROW_ETAG")
    String etag
}
