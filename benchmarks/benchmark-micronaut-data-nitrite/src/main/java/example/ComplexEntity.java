package example;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;

import java.net.URL;
import java.util.Optional;
import java.util.UUID;

/**
 * Complex entity for testing all PropertyStrategy enum cases.
 */
@MappedEntity
public final class ComplexEntity implements java.io.Serializable {
    @Id
    @GeneratedValue
    private String id;

    // JAVA_PASSTHROUGH strategies
    private String name;
    private int count;
    private boolean active;

    // Specific type strategies
    private UUID uuid;
    private URL url;
    private Status status;  // ENUM
    private Optional<String> description;  // OPTIONAL
    private Tag tag;  // SERDE (@Introspected POJO)

    // Association strategies
    @Relation(Relation.Kind.MANY_TO_ONE)
    private Author author;

    public ComplexEntity() {
    }

    public ComplexEntity(String name, int count, boolean active, UUID uuid, URL url, Status status,
                         Optional<String> description, Tag tag, Author author) {
        this.name = name;
        this.count = count;
        this.active = active;
        this.uuid = uuid;
        this.url = url;
        this.status = status;
        this.description = description;
        this.tag = tag;
        this.author = author;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    public URL getUrl() {
        return url;
    }

    public void setUrl(URL url) {
        this.url = url;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public Optional<String> getDescription() {
        return description;
    }

    public void setDescription(Optional<String> description) {
        this.description = description;
    }

    public Tag getTag() {
        return tag;
    }

    public void setTag(Tag tag) {
        this.tag = tag;
    }

    public Author getAuthor() {
        return author;
    }

    public void setAuthor(Author author) {
        this.author = author;
    }
}
