package example;

import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;

/**
 * Repository for Author with association queries.
 */
@NitriteRepository
public interface AuthorRepository extends CrudRepository<Author, String> {

    Author findByName(String name);

    // Test reverse lookup through ONE_TO_MANY
    List<Author> findByBooksTitle(String title);
}
