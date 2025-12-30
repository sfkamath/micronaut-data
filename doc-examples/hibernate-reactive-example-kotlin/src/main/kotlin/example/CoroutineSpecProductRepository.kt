package example

import io.micronaut.data.annotation.Repository
import io.micronaut.data.model.Page
import io.micronaut.data.model.Pageable
import io.micronaut.data.repository.jpa.criteria.QuerySpecification
import io.micronaut.data.repository.jpa.kotlin.CoroutineJpaSpecificationExecutor
import io.micronaut.data.repository.kotlin.CoroutinePageableCrudRepository
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Root

/**
 * Kotlin coroutine repository that combines PageableCrud + CoroutineJpaSpecificationExecutor,
 * with a default method that uses QuerySpecification and a correlated subquery.
 * This mirrors the reported failing pattern (subquery.correlate(root)).
 */
@Repository
interface CoroutineSpecProductRepository :
    CoroutinePageableCrudRepository<Product, Long>,
    CoroutineJpaSpecificationExecutor<Product> {

    /**
     * Builds a QuerySpecification with a correlated subquery and executes it via findAll(spec, pageable).
     */
    suspend fun failingFunction(pageable: Pageable): Page<Product> {
        val spec = QuerySpecification<Product> { root: Root<Product>, query: CriteriaQuery<*>, cb: CriteriaBuilder ->
            // Ensure we are using Hibernate criteria APIs (not Micronaut runtime fallback)
            require(cb.javaClass.name.startsWith("org.hibernate")) {
                "Expected Hibernate CriteriaBuilder, got: ${cb.javaClass.name}"
            }
            val sub = query.subquery(Long::class.java)
            val correlated = sub.correlate(root) // This is the previously failing line if runtime criteria was used
            sub.select(cb.literal(1L))
            sub.where(
                cb.equal(
                    correlated.get<String>("name"),
                    root.get<String>("name")
                )
            )
            cb.exists(sub)
        }
        return findAll(spec, pageable)
    }
}
