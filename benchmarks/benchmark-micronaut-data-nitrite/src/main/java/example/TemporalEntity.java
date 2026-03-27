package example;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Entity with all temporal types for benchmarking epoch storage.
 */
@MappedEntity
public final class TemporalEntity implements java.io.Serializable {
    @Id
    @GeneratedValue
    private String id;
    private Instant instant;
    private LocalDate localDate;
    private LocalDateTime localDateTime;
    private LocalTime localTime;
    private String name;

    public TemporalEntity() {
    }

    public TemporalEntity(String name, Instant instant, LocalDate localDate, LocalDateTime localDateTime, LocalTime localTime) {
        this.name = name;
        this.instant = instant;
        this.localDate = localDate;
        this.localDateTime = localDateTime;
        this.localTime = localTime;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Instant getInstant() {
        return instant;
    }

    public void setInstant(Instant instant) {
        this.instant = instant;
    }

    public LocalDate getLocalDate() {
        return localDate;
    }

    public void setLocalDate(LocalDate localDate) {
        this.localDate = localDate;
    }

    public LocalDateTime getLocalDateTime() {
        return localDateTime;
    }

    public void setLocalDateTime(LocalDateTime localDateTime) {
        this.localDateTime = localDateTime;
    }

    public LocalTime getLocalTime() {
        return localTime;
    }

    public void setLocalTime(LocalTime localTime) {
        this.localTime = localTime;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
