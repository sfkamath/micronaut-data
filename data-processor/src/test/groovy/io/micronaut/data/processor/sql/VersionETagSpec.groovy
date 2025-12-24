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

import io.micronaut.data.annotation.sql.ETag
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder
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

        then:
        // Expect the WHERE left-hand side to use the read transformer and alias replacement:
        // Note: UPDATE uses no table alias by default, so @. resolves to empty prefix -> properties without alias
        sql == 'UPDATE "book" SET "title"=? WHERE (SYS_ROW_ETAG(id, title) = ?)'
    }
}

import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Id

@MappedEntity("book")
class ETagBook {
    @Id
    Long id
    String title
    @ETag(function = "SYS_ROW_ETAG", fields = ["id", "title"])
    String etag
}
