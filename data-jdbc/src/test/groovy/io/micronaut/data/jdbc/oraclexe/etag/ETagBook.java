package io.micronaut.data.jdbc.oraclexe.etag;

import io.micronaut.data.annotation.*;
import io.micronaut.data.annotation.sql.ETag;

@MappedEntity("etag_book")
public record ETagBook(
    @Id
    @GeneratedValue
    Long id,
    String title,

    @ETag(function = "SYS_ROW_ETAG", fields = {"id", "title"})
    String etag) {
}
