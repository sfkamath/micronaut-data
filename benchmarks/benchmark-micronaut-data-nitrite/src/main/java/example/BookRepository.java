package example;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.repository.CrudRepository;
import java.util.List;

/**
 * The book repository.
 */
@NitriteRepository
public interface BookRepository extends CrudRepository<Book, String> {
    Book findByTitle(String title);

    // Exercises Metadata-Aware Coercion (int type is known from entity)
    List<Book> findByPages(int pages);

    // Exercises Dynamic Query fallback/deduplication
    @Query("{\"pages\": {\"$eq\": :pages}}")
    List<Book> findByPagesQuery(int pages);

    // Association queries (MANY_TO_ONE)
    List<Book> findByAuthorId(String authorId);
    List<Book> findByAuthor(Author author);

    // Regex/LIKE query - tests pre-compiled Pattern optimization
    @Query("{\"title\": {\"$regex\": :regex}}")
    List<Book> findByTitleRegex(String regex);

    // Scalar projections - tests convertValue() for projected results
    int countByPagesGreaterThan(int pages);
}
