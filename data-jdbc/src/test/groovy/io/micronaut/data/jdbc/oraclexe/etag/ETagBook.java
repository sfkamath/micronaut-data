package io.micronaut.data.jdbc.oraclexe.etag;

import io.micronaut.data.annotation.*;
import io.micronaut.data.annotation.sql.ColumnTransformer;

@MappedEntity("etag_book")
public class ETagBook {
    @Id
    @GeneratedValue
    private Long id;

    private String title;

    // Computed version from Oracle SYS_ROW_ETAG(id, title)
    @Version
    @GeneratedValue
    @ColumnTransformer(read = "SYS_ROW_ETAG(@.id, @.title)")
    private String etag;

    public ETagBook() {
    }

    public ETagBook(String title) {
        this.title = title;
    }

    public ETagBook(Long id, String title, String etag) {
        this.id = id;
        this.title = title;
        this.etag = etag;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getEtag() {
        return etag;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setEtag(String etag) {
        this.etag = etag;
    }
}
