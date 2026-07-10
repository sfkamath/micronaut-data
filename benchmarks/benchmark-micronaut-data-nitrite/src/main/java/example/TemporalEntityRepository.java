package example;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.repository.CrudRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * Repository for TemporalEntity with temporal type queries.
 */
@NitriteRepository
public interface TemporalEntityRepository extends CrudRepository<TemporalEntity, String> {

    // Temporal equality queries
    List<TemporalEntity> findByInstant(Instant instant);
    List<TemporalEntity> findByLocalDate(LocalDate localDate);
    List<TemporalEntity> findByLocalDateTime(LocalDateTime localDateTime);
    List<TemporalEntity> findByLocalTime(LocalTime localTime);

    // Temporal range queries
    List<TemporalEntity> findByInstantAfter(Instant instant);
    List<TemporalEntity> findByLocalDateBetween(LocalDate start, LocalDate end);
    List<TemporalEntity> findByLocalDateTimeGreaterThan(LocalDateTime localDateTime);

    // Named query for temporal range (tests pre-compiled AST with epoch values)
    @Query("{\"localDate\": {\"$gte\": :start, \"$lte\": :end}}")
    List<TemporalEntity> findByDateRange(LocalDate start, LocalDate end);
}
