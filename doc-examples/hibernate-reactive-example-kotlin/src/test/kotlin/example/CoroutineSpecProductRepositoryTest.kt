package example

import io.micronaut.data.model.Pageable
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@MicronautTest(transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CoroutineSpecProductRepositoryTest : PostgresHibernateReactiveProperties {

    @Inject
    lateinit var productRepository: ProductRepository

    @Inject
    lateinit var manufacturerRepository: ManufacturerRepository

    @Inject
    lateinit var specRepository: CoroutineSpecProductRepository

    @BeforeAll
    fun setupData() : Unit = runBlocking {
        productRepository.deleteAll()
        manufacturerRepository.deleteAll()
        val apple = manufacturerRepository.save("Apple")
        productRepository.saveAll(
            listOf(
                Product(null, "MacBook", apple),
                Product(null, "iPhone", apple)
            )
        )
    }

    @Test
    fun `correlated subquery via QuerySpecification in coroutine repository succeeds`() = runBlocking {
        val page = specRepository.failingFunction(Pageable.from(0, 10))
        // Validate that the call succeeds (no IllegalStateException from correlate(root))
        assertTrue(page != null)
    }
}
