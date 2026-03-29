package io.micronaut.data.nitrite.repository

import io.micronaut.data.nitrite.model.Person
import io.micronaut.data.nitrite.operations.NitriteRepositoryOperations
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.dizitart.no2.collection.events.CollectionEventInfo
import org.dizitart.no2.collection.events.CollectionEventListener
import org.dizitart.no2.collection.events.EventType
import spock.lang.Specification

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Tests for Nitrite collection event listener API.
 */
@MicronautTest(transactional = false)
class PersonEventListenerSpec extends Specification {

    @Inject
    PersonRepository personRepository

    @Inject
    NitriteRepositoryOperations repositoryOperations

    def setup() {
        personRepository.deleteAll()
    }

    void "test subscribe and receive insert events"() {
        given: "A listener with a countdown latch"
        def latch = new CountDownLatch(1)
        def events = []
        CollectionEventListener listener = { CollectionEventInfo<?> event ->
            events << event
            latch.countDown()
        }

        when: "Subscribe to events and insert a person"
        def subscription = repositoryOperations.subscribeCollectionEventListener(Person, listener)
        def person = new Person("EventTest", 25)
        personRepository.save(person)

        then: "Event listener is notified"
        latch.await(5, TimeUnit.SECONDS)
        events.size() == 1
        events[0].eventType == EventType.Insert
        events[0].item != null

        cleanup:
        repositoryOperations.unsubscribeCollectionEventListener(subscription)
    }

    void "test subscribe and receive update events"() {
        given: "A listener and a saved person"
        def latch = new CountDownLatch(2) // Expect 2 events: insert + update
        def events = []
        CollectionEventListener listener = { CollectionEventInfo<?> event ->
            events << event
            latch.countDown()
        }
        def subscription = repositoryOperations.subscribeCollectionEventListener(Person, listener)
        def person = personRepository.save(new Person("UpdateTest", 30))

        when: "Update the person"
        person.age = 31
        personRepository.update(person)

        then: "Update event is received"
        latch.await(5, TimeUnit.SECONDS)
        events.find { it.eventType == EventType.Update } != null

        cleanup:
        repositoryOperations.unsubscribeCollectionEventListener(subscription)
    }

    void "test subscribe and receive delete events"() {
        given: "A listener and a saved person"
        def latch = new CountDownLatch(2) // Expect 2 events: insert + remove
        def events = []
        CollectionEventListener listener = { CollectionEventInfo<?> event ->
            events << event
            latch.countDown()
        }
        def subscription = repositoryOperations.subscribeCollectionEventListener(Person, listener)
        def person = personRepository.save(new Person("DeleteTest", 40))

        when: "Delete the person"
        personRepository.deleteById(person.id)

        then: "Delete event is received"
        latch.await(5, TimeUnit.SECONDS)
        events.find { it.eventType == EventType.Remove } != null

        cleanup:
        repositoryOperations.unsubscribeCollectionEventListener(subscription)
    }

    void "test unsubscribe stops receiving events"() {
        given: "A listener that is subscribed then unsubscribed"
        def latch = new CountDownLatch(1)
        def events = []
        CollectionEventListener listener = { CollectionEventInfo<?> event ->
            events << event
            latch.countDown()
        }
        def subscription = repositoryOperations.subscribeCollectionEventListener(Person, listener)
        repositoryOperations.unsubscribeCollectionEventListener(subscription)

        when: "Insert a person after unsubscribe"
        def person = new Person("AfterUnsubscribe", 50)
        personRepository.save(person)
        latch.await(1, TimeUnit.SECONDS)

        then: "No events received"
        events.size() == 0
    }

    void "test multiple listeners receive events"() {
        given: "Two listeners subscribed"
        def latch1 = new CountDownLatch(1)
        def latch2 = new CountDownLatch(1)
        def events1 = []
        def events2 = []
        CollectionEventListener listener1 = { CollectionEventInfo<?> event ->
            events1 << event
            latch1.countDown()
        }
        CollectionEventListener listener2 = { CollectionEventInfo<?> event ->
            events2 << event
            latch2.countDown()
        }
        def subscription1 = repositoryOperations.subscribeCollectionEventListener(Person, listener1)
        def subscription2 = repositoryOperations.subscribeCollectionEventListener(Person, listener2)

        when: "Insert a person"
        def person = new Person("MultiListener", 35)
        personRepository.save(person)

        then: "Both listeners receive the event"
        latch1.await(5, TimeUnit.SECONDS)
        latch2.await(5, TimeUnit.SECONDS)
        events1.size() == 1
        events2.size() == 1

        cleanup:
        repositoryOperations.unsubscribeCollectionEventListener(subscription1)
        repositoryOperations.unsubscribeCollectionEventListener(subscription2)
    }
}
