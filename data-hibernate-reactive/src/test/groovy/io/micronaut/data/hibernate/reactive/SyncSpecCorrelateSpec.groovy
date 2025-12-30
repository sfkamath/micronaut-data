package io.micronaut.data.hibernate.reactive

import io.micronaut.data.annotation.Repository
import io.micronaut.data.repository.CrudRepository
import io.micronaut.data.repository.jpa.JpaSpecificationExecutor
import io.micronaut.data.repository.jpa.criteria.QuerySpecification
import io.micronaut.data.tck.entities.Person
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Root
import spock.lang.Shared
import spock.lang.Specification

/**
 * Test if subquery correlate delegates call to hibernate criteria.
 */
@MicronautTest(transactional = false, packages = "io.micronaut.data.tck.entities")
class SyncSpecCorrelateSpec extends Specification implements PostgresHibernateReactiveProperties {

    @Inject
    @Shared
    SyncSpecPersonRepository syncRepo

    // Use reactive repo to insert test data (sync persist is not supported by reactive sync ops)
    @Inject
    @Shared
    JpaSpecificationCrudRepository reactiveRepo

    def setupSpec() {
        if (reactiveRepo.findByName("Jeff").block() == null) {
            reactiveRepo.save(new Person(name: "Jeff", age: 50)).block()
        }
    }

    void "synchronous JpaSpecificationExecutor uses Hibernate Criteria (correlate on subquery works)"() {
        given:
        QuerySpecification<Person> spec = { Root<Person> root, CriteriaQuery<?> query, CriteriaBuilder cb ->
            if (!cb.class.name.startsWith("org.hibernate")) {
                throw new IllegalStateException("Expected Hibernate CriteriaBuilder, got: " + cb.class.name)
            }
            def sub = query.subquery(Long)
            def correlated = sub.correlate(root)  // this used to fail with Runtime subquery
            sub.select(cb.literal(1L))
            sub.where(cb.equal(correlated.get("name"), root.get("name")))
            return cb.exists(sub)
        } as QuerySpecification<Person>

        when:
        def results = syncRepo.findAll(spec)

        then:
        results != null
        results.size() >= 1
        results*.name.contains("Jeff")
    }
}

/**
 * Synchronous repository intentionally used in a Hibernate Reactive test module
 * to exercise AbstractSpecificationInterceptor with DefaultHibernateReactiveSynchronousRepositoryOperations.
 */
@Repository
interface SyncSpecPersonRepository extends CrudRepository<Person, Long>, JpaSpecificationExecutor<Person> {
}
