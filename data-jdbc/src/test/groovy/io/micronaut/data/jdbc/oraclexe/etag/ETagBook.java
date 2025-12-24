package io.micronaut.data.jdbc.oraclexe.etag;

import io.micronaut.data.annotation.*;
import io.micronaut.data.annotation.sql.ETag;
import io.micronaut.data.annotation.sql.ETagPart;

@MappedEntity("etag_book")
public record ETagBook(
    @Id
    @GeneratedValue
    @ETagPart
    Long id,
    @ETagPart
    String title,

    @Relation(Relation.Kind.EMBEDDED)
    BookDetails bookDetails,

    @ETag(function = "SYS_ROW_ETAG")
    String etag) {

    @Embeddable
    public record BookDetails(
        @ETagPart
        int pages,
        int chapters
    ) {
    }
}
