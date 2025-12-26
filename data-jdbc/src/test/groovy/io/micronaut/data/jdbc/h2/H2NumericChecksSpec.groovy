package io.micronaut.data.jdbc.h2

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.model.PersistentEntity
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder
import spock.lang.Specification

import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Negative
import jakarta.validation.constraints.NegativeOrZero
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.PositiveOrZero

class H2NumericChecksSpec extends Specification {

    void "test create table with numeric checks (H2)"() {
        given:
        SqlQueryBuilder builder = new SqlQueryBuilder(Dialect.H2)

        when:
        def sql = builder.buildBatchCreateTableStatement(PersistentEntity.of(NumericEntity))

        then:
        // Table and columns are created
        sql.contains('CREATE TABLE `numeric_entity`')
        sql.contains('`id` BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY')
        sql.contains('`positive` INT NOT NULL')
        sql.contains('`positive_or_zero` INT NOT NULL')
        sql.contains('`min_v` INT NOT NULL')
        sql.contains('`max_v` INT NOT NULL')
        sql.contains('`dec_min` DECIMAL')
        sql.contains('`dec_max` DECIMAL')

        // Derived CHECK constraints for numeric bean validation annotations
        sql.contains('CONSTRAINT `ck_numeric_entity_positive_gt_0` CHECK (`positive` > 0)')
        sql.contains('CONSTRAINT `ck_numeric_entity_positive_or_zero_ge_0` CHECK (`positive_or_zero` >= 0)')
        sql.contains('CONSTRAINT `ck_numeric_entity_min_v_ge_5` CHECK (`min_v` >= 5)')
        sql.contains('CONSTRAINT `ck_numeric_entity_max_v_le_10` CHECK (`max_v` <= 10)')
        sql.contains('CONSTRAINT `ck_numeric_entity_dec_min_gt_1_5` CHECK (`dec_min` > 1.5)')
        sql.contains('CONSTRAINT `ck_numeric_entity_dec_max_le_10_5` CHECK (`dec_max` <= 10.5)')
    }
}

@MappedEntity
class NumericEntity {
    @Id
    @GeneratedValue
    Long id

    @Positive
    int positive

    @PositiveOrZero
    int positiveOrZero

    @Min(5L)
    int minV

    @Max(10L)
    int maxV

    @DecimalMin(value = "1.5", inclusive = false)
    BigDecimal decMin

    @DecimalMax(value = "10.5", inclusive = true)
    BigDecimal decMax
}
