package example;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.repository.CrudRepository;

import java.net.URL;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for ComplexEntity testing all PropertyStrategy cases.
 */
@NitriteRepository
public interface ComplexEntityRepository extends CrudRepository<ComplexEntity, String> {

    // JAVA_PASSTHROUGH strategies
    List<ComplexEntity> findByName(String name);
    List<ComplexEntity> findByCount(int count);
    List<ComplexEntity> findByActive(boolean active);

    // UUID strategy
    List<ComplexEntity> findByUuid(UUID uuid);

    // URL strategy
    List<ComplexEntity> findByUrl(URL url);

    // ENUM strategy
    List<ComplexEntity> findByStatus(Status status);

    // OPTIONAL strategy
    List<ComplexEntity> findByDescription(Optional<String> description);

    // SERDE strategy (@Introspected POJO)
    List<ComplexEntity> findByTag(Tag tag);

    // Association strategy (MANY_TO_ONE)
    List<ComplexEntity> findByAuthorId(String authorId);

    // Named query testing pre-compiled AST with multiple property types
    @Query("{\"uuid\": {\"$eq\": :uuid}, \"status\": {\"$eq\": :status}}")
    List<ComplexEntity> findByUuidAndStatus(UUID uuid, Status status);
}
