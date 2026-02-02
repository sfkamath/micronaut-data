package io.micronaut.data.jdbc.oraclexe.etag

import groovy.transform.Memoized
import io.micronaut.context.ApplicationContext
import io.micronaut.data.exceptions.OptimisticLockException
import io.micronaut.data.jdbc.oraclexe.OracleTestPropertyProvider
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class OracleETagVersionSpec extends Specification implements OracleTestPropertyProvider { 

    @Memoized
    ETagBookExplicitRepository getExplicitRepo() {
        context.getBean(ETagBookExplicitRepository)
    }

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties)

    @Memoized
    ETagBookRepository getRepo() {
        context.getBean(ETagBookRepository)
    }

    void "ETag is computed on read and used for optimistic locking (implicit via @Etaggable)"() {
        when: "save a new book"
        def b = repo.save(new ETagBook(null, "Initial", new ETagBook.BookDetails(200, 10), null))
        def opt = repo.findById(b.id())
        then:
        opt.present
        opt.get().etag() != null

        when: "optimistic update succeeds with fresh etag"
        def fresh = repo.findById(b.id()).get()
        def etag1 = fresh.etag()
        fresh = new ETagBook(fresh.id(), "Updated-1", fresh.bookDetails(), etag1)
        repo.update(fresh)
        def afterUpdate = repo.findById(b.id()).get()
        def etag2 = afterUpdate.etag()
        then:
        etag2 != null
        etag2 != etag1

        when: "optimistic update fails with stale etag"
        def stale = new ETagBook(b.id(), "Updated-2", new ETagBook.BookDetails(201, 20), etag1) // use stale etag captured before successful update
        repo.update(stale)
        then:
        def ex = thrown(OptimisticLockException)
        ex.message == "Execute update returned unexpected row count. Expected: 1 got: 0"
    }

    void "ETag is computed on read and used for optimistic locking (explicit @ETagValue)"() {
        when: "save a new book"
        def b = explicitRepo.save(new ETagBookExplicit(null, "Initial", new ETagBookExplicit.BookDetails(200, 10), null))
        def opt = explicitRepo.findById(b.id())
        then:
        opt.present
        opt.get().etag() != null

        when: "optimistic update succeeds with fresh etag"
        def fresh = explicitRepo.findById(b.id()).get()
        def etag1 = fresh.etag()
        fresh = new ETagBookExplicit(fresh.id(), "Updated-1", fresh.bookDetails(), etag1)
        explicitRepo.update(fresh)
        def afterUpdate = explicitRepo.findById(b.id()).get()
        def etag2 = afterUpdate.etag()
        then:
        etag2 != null
        etag2 != etag1

        when: "optimistic update fails with stale etag"
        def stale = new ETagBookExplicit(b.id(), "Updated-2", new ETagBookExplicit.BookDetails(201, 20), etag1)
        explicitRepo.update(stale)
        then:
        def ex = thrown(OptimisticLockException)
        ex.message == "Execute update returned unexpected row count. Expected: 1 got: 0"
    }
}
