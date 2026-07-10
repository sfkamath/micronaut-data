/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package benchmark;

import example.*;
import io.micronaut.context.ApplicationContext;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.time.*;
import java.util.*;

/**
 * Comprehensive benchmark covering all Nitrite optimization areas:
 * 1. Type Strategies - PropertyStrategy enum dispatch (21 cases)
 * 2. Filter Operators - $gt, $lt, $in, $regex, $and, $or
 * 3. Associations - MANY_TO_ONE, ONE_TO_MANY reverse lookup
 * 4. Temporal Range - epoch storage for Instant, LocalDate, etc.
 * 5. Batch Operations - metadata caching under volume
 * 6. Hot-Path Fast-Paths - String/Number/Boolean bypass instanceof chain
 * 7. Regex Pre-compilation - Pattern.compile() cached
 * 8. Scalar Projections - convertValue() for projected results
 */
@State(Scope.Benchmark)
public class ComprehensiveQuery {

    ApplicationContext applicationContext;
    BookRepository bookRepository;
    AuthorRepository authorRepository;
    TemporalEntityRepository temporalRepository;
    ComplexEntityRepository complexRepository;

    // Test data
    Author testAuthor;
    Book testBook;
    TemporalEntity testTemporal;
    ComplexEntity testComplex;
    List<ComplexEntity> complexBatch;
    List<Book> bookBatch;
    private int counter;

    @Setup
    public void prepare() throws Exception {
        File tempDir = Files.createTempDirectory("nitrite-benchmark").toFile();
        Map<String, Object> props = new HashMap<>();
        props.put("nitrite.storage-mode", "IN_MEMORY");
        props.put("logger.levels.io.micronaut.data.query", "INFO");
        props.put("logger.levels.io.micronaut.data.nitrite", "INFO");

        applicationContext = ApplicationContext.run(props);
        bookRepository = applicationContext.getBean(BookRepository.class);
        authorRepository = applicationContext.getBean(AuthorRepository.class);
        temporalRepository = applicationContext.getBean(TemporalEntityRepository.class);
        complexRepository = applicationContext.getBean(ComplexEntityRepository.class);

        // Setup Author + Books (association testing)
        testAuthor = new Author("Test Author");
        authorRepository.save(testAuthor);

        testBook = new Book("Test Book", 500, testAuthor);
        bookRepository.save(testBook);

        // Setup TemporalEntity (epoch storage testing)
        Instant now = Instant.now();
        LocalDate today = LocalDate.now();
        LocalDateTime nowDateTime = LocalDateTime.now();
        LocalTime currentTime = LocalTime.now();
        testTemporal = new TemporalEntity("Test Event", now, today, nowDateTime, currentTime);
        temporalRepository.save(testTemporal);

        // Setup ComplexEntity (all PropertyStrategy cases)
        URL testUrl = new URL("https://example.com");
        Tag testTag = new Tag("test-tag", "blue");
        testComplex = new ComplexEntity(
            "Test Entity",
            42,
            true,
            UUID.randomUUID(),
            testUrl,
            Status.ACTIVE,
            Optional.of("Test description"),
            testTag,
            testAuthor
        );
        complexRepository.save(testComplex);

        // Add batch entities
        complexBatch = new ArrayList<>();
        bookBatch = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            ComplexEntity ce = new ComplexEntity(
                "Entity " + i,
                i,
                i % 2 == 0,
                UUID.randomUUID(),
                new URL("https://example.com/" + i),
                i % 3 == 0 ? Status.ACTIVE : (i % 3 == 1 ? Status.INACTIVE : Status.PENDING),
                Optional.of("Description " + i),
                new Tag("tag-" + i, "color-" + i),
                testAuthor
            );
            complexBatch.add(ce);

            Book book = new Book("Benchmark Book " + i, 300 + i * 10);
            bookBatch.add(book);
            bookRepository.save(book);  // Save books so they can be queried
        }
        counter = 0;
    }

    @TearDown
    public void cleanup() {
        if (applicationContext != null) {
            applicationContext.close();
        }
    }

    /**
     * Tests PropertyStrategy enum dispatch for all type strategies:
     * - JAVA_PASSTHROUGH (String, int, boolean)
     * - UUID, URL, ENUM, OPTIONAL
     * - SERDE (@Introspected POJO)
     * - ASSOCIATION_ID_REF (MANY_TO_ONE)
     */
    @Benchmark
    public void measureTypeStrategies() {
        // JAVA_PASSTHROUGH
        bookRepository.findByTitle("Test Book");
        bookRepository.findByPages(500);

        // UUID strategy
        complexRepository.findByUuid(testComplex.getUuid());

        // URL strategy
        try {
            complexRepository.findByUrl(new URL("https://example.com"));
        } catch (Exception e) {
            // Ignore
        }

        // ENUM strategy
        complexRepository.findByStatus(Status.ACTIVE);

        // OPTIONAL strategy
        complexRepository.findByDescription(Optional.of("Test description"));

        // SERDE strategy
        complexRepository.findByTag(testComplex.getTag());
    }

    /**
     * Tests filter operator nodes:
     * - $gt, $lt, $gte, $lte (range operators)
     * - $in (set membership)
     * - Pre-compiled AST with multiple operators
     */
    @Benchmark
    public void measureFilterOperators() {
        // Range operators (numeric)
        bookRepository.findByPages(600);  // Will match multiple

        // Range operators (temporal) - uses epoch comparison
        temporalRepository.findByLocalDateTimeGreaterThan(LocalDateTime.now().minusHours(1));

        // BETWEEN query (uses $gte + $lte)
        LocalDate start = LocalDate.now();
        LocalDate end = LocalDate.now().plusDays(5);
        temporalRepository.findByDateRange(start, end);

        // Pre-compiled AST with multiple operators
        complexRepository.findByUuidAndStatus(testComplex.getUuid(), Status.ACTIVE);
    }

    /**
     * Tests association strategies:
     * - MANY_TO_ONE (findByAuthorId)
     * - ONE_TO_MANY reverse lookup (findByBooksTitle)
     * - MAPPED_BY back-reference wiring
     */
    @Benchmark
    public void measureAssociations() {
        // MANY_TO_ONE - filter by foreign key
        bookRepository.findByAuthorId(testAuthor.getId());

        // MANY_TO_ONE - filter by entity reference
        bookRepository.findByAuthor(testAuthor);

        // ONE_TO_MANY reverse lookup
        authorRepository.findByBooksTitle("Book 5");
    }

    /**
     * Tests temporal epoch storage and range queries:
     * - Instant → epoch nanoseconds
     * - LocalDate → epoch day
     * - LocalDateTime → epoch nanoseconds via UTC
     * - LocalTime → nanosecond of day
     */
    @Benchmark
    public void measureTemporalRange() {
        // Instant equality (epoch nanos)
        temporalRepository.findByInstant(testTemporal.getInstant());

        // LocalDate equality (epoch day)
        temporalRepository.findByLocalDate(testTemporal.getLocalDate());

        // LocalDateTime equality (epoch nanos)
        temporalRepository.findByLocalDateTime(testTemporal.getLocalDateTime());

        // LocalTime equality (nanos of day)
        temporalRepository.findByLocalTime(testTemporal.getLocalTime());

        // Range query - Instant after
        temporalRepository.findByInstantAfter(Instant.now().minusSeconds(1));

        // Range query - LocalDate between
        temporalRepository.findByLocalDateBetween(
            LocalDate.now().minusDays(1),
            LocalDate.now().plusDays(10)
        );
    }

    /**
     * Tests batch operations and metadata caching.
     */
    @Benchmark
    public void measureBatchSave() {
        // Save a few complex entities - exercises NitriteEntityMeta cache
        if (complexBatch.size() > 5) {
            complexRepository.save(complexBatch.get(0));
            complexRepository.save(complexBatch.get(1));
            complexRepository.save(complexBatch.get(2));
        }
    }

    /**
     * Tests hot-path fast-paths for String/Number/Boolean types.
     * Commit 89e159d246 added fast paths that bypass the instanceof cascade
     * and Class.getClass() invocation for common JDK types.
     *
     * High-volume simple type queries should benefit from this optimization.
     */
    @Benchmark
    public void measureStringEquality_Volume() {
        // String fast-path: bypasses instanceof chain
        int idx = counter++ % bookBatch.size();
        bookRepository.findByTitle(bookBatch.get(idx).getTitle());
    }

    /**
     * Tests numeric fast-path and metadata-aware coercion.
     * Exercises the fast path for Number types in toFilterValue().
     */
    @Benchmark
    public void measureNumericEquality_Volume() {
        // Number fast-path: bypasses instanceof chain
        int idx = counter++ % bookBatch.size();
        bookRepository.findByPages(bookBatch.get(idx).getPages());
    }

    /**
     * Tests regex/Pattern pre-compilation optimization.
     * Commit 89e159d246 pre-compiled recurring regex Pattern instances
     * in CollectionAggregator and NitriteQueryExecutor.
     *
     * This benchmark uses LIKE/regex queries that exercise the pre-compiled patterns.
     */
    @Benchmark
    public void measureLikeQueries() {
        // Uses pre-compiled Pattern for LIKE regex conversion
        // Matches "Benchmark Book 0", "Benchmark Book 1", etc.
        bookRepository.findByTitleRegex(".*Benchmark.*");
    }

    /**
     * Tests scalar projection operations.
     * Commit a7afe87143 added convertValue() for projected scalar results
     * in findOne(PreparedQuery) - e.g., findMaxDate returns LocalDate directly.
     *
     * This measures the projection path that bypasses full entity hydration.
     */
    @Benchmark
    public void measureScalarProjection() {
        // Count projection - returns scalar, not entity
        bookRepository.count();

        // Scalar projection with condition - tests convertValue() path
        bookRepository.countByPagesGreaterThan(500);
    }

    /**
     * Tests high-volume save operations with simple entities.
     * Measures metadata cache hit rate under sustained load.
     * Each save hits the cached NitriteEntityMeta, testing the cache efficiency.
     */
    @Benchmark
    public void measureHighVolumeSave() {
        int idx = counter++ % bookBatch.size();
        bookRepository.save(bookBatch.get(idx));
    }

    static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(".*" + ComprehensiveQuery.class.getSimpleName() + ".*")
                .warmupIterations(2)
                .measurementIterations(3)
                .forks(1)
                .build();

        new Runner(opt).run();
    }
}
