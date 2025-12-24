package io.micronaut.data.processor.sql

import io.micronaut.data.annotation.DataTransformer
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Version
import io.micronaut.data.processor.visitors.AbstractDataSpec

class ETagMapperSpec extends AbstractDataSpec {

    void "mapper expands @ETag(function, fields) to Version + GeneratedValue + ColumnTransformer(read=function(@.fields..))"() {
        given:
        def entity = buildEntity('test.Book', '''
import io.micronaut.data.annotation.*;
import io.micronaut.data.annotation.sql.ETag;

@MappedEntity
class Book {
    @Id
    @GeneratedValue
    Long id;

    String title;

    @ETag(function = "SYS_ROW_ETAG", fields = {"id", "title"})
    String etag;

    public String getEtag() { return etag; }

    public Book() {}
}
''')

        expect: "Version and GeneratedValue are present on the mapped property"
        def prop = entity.getPropertyByName("etag")
        prop.annotationMetadata.hasStereotype(Version)
        prop.annotationMetadata.hasStereotype(GeneratedValue)

        and: "ColumnTransformer(read=SYS_ROW_ETAG(@.id, @.title)) is applied"
        prop.annotationMetadata.stringValue(DataTransformer, "read").get() == 'SYS_ROW_ETAG(@.id, @.title)'
    }
}
