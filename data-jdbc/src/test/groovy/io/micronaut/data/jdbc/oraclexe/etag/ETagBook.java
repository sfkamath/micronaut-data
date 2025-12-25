package io.micronaut.data.jdbc.oraclexe.etag;

import io.micronaut.data.annotation.*;
import io.micronaut.data.annotation.sql.ETagValueBased;
import io.micronaut.data.annotation.sql.ETagValue;

@MappedEntity("etag_book")
public record ETagBook(
    @Id
    @GeneratedValue
    @ETagValue
    Long id,
    @ETagValue
    String title,

    @Relation(Relation.Kind.EMBEDDED)
    BookDetails bookDetails,

    @ETagValueBased(function = "SYS_ROW_ETAG")
    String etag) {

    @Embeddable
    public record BookDetails(
        @ETagValue
        int pages,
        int chapters
    ) {
    }
}
