/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.data.model.query.builder.sql;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Index;
import io.micronaut.data.annotation.Indexes;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.annotation.sql.SqlMembers;
import io.micronaut.data.exceptions.MappingException;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentEntityUtils;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.PersistentPropertyPath;
import io.micronaut.data.model.naming.NamingStrategy;
import io.micronaut.data.model.schema.sql.SqlColumnMapping;
import io.micronaut.data.model.schema.sql.SqlDbType;
import io.micronaut.data.model.schema.sql.SqlIndexMapping;
import io.micronaut.data.model.schema.sql.SqlSequenceMapping;
import io.micronaut.data.model.schema.sql.SqlTableMapping;
import io.micronaut.data.model.schema.sql.SqlCheckConstraint;

import java.lang.annotation.Annotation;
import java.sql.Blob;
import java.sql.Clob;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Stream;

import static io.micronaut.core.annotation.AnnotationMetadata.VALUE_MEMBER;
import static io.micronaut.data.annotation.GeneratedValue.Type.AUTO;

/**
 * Utility class providing methods for working with SQL schema definitions.
 *
 * @author radovanradic
 * @since 4.13.0
 */
@Internal
public final class SqlSchemaUtils {

    // Table and column metadata columns
    public static final String TABLE_TYPE = "TABLE";
    public static final String TABLE_CATALOG_COLUMN = "TABLE_CAT";
    public static final String TABLE_SCHEMA_COLUMN = "TABLE_SCHEM";
    public static final String TABLE_NAME_COLUMN = "TABLE_NAME";
    public static final String COLUMN_NAME_COLUMN = "COLUMN_NAME";
    public static final String DATA_TYPE_COLUMN = "DATA_TYPE";
    public static final String TYPE_NAME_COLUMN = "TYPE_NAME";
    public static final String COLUMN_SIZE_COLUMN = "COLUMN_SIZE";
    public static final String DECIMAL_DIGITS_COLUMN = "DECIMAL_DIGITS";
    public static final String NULLABLE_COLUMN = "NULLABLE";

    private SqlSchemaUtils() {
    }

    /**
     * Returns list of {@link SqlTableMapping} for persistent entity. It will contain main entity table
     * and potentially joined tables.
     *
     * @param entity The entity
     * @return The SQL table definitions for the given entity
     * @since 4.13.0
     */
    @Experimental

    @SuppressWarnings("java:S3776")
    public static List<SqlTableMapping> getSqlTableMappings(PersistentEntity entity) {
        ArgumentUtils.requireNonNull("entity", entity);

        final String tableName = entity.getPersistedName();
        String schema = SqlQueryBuilderUtils.getSchemaName(entity);
        boolean escape = entity.getAnnotationMetadata().booleanValue(MappedEntity.class, "escape").orElse(true);

        List<SqlTableMapping> tables = new ArrayList<>();

        Collection<Association> foreignKeyAssociations = SqlQueryBuilderUtils.getJoinTableAssociations(entity);

        NamingStrategy namingStrategy = entity.getNamingStrategy();
        if (CollectionUtils.isNotEmpty(foreignKeyAssociations)) {
            for (Association association : foreignKeyAssociations) {
                PersistentEntity associatedEntity = association.getAssociatedEntity();
                List<SqlColumnMapping> columns = new ArrayList<>();

                Optional<Association> inverseSide = association.getInverseSide().map(Function.identity());
                Association owningAssociation = inverseSide.orElse(association);
                AnnotationMetadata annotationMetadata = owningAssociation.getAnnotationMetadata();

                String joinTableName = annotationMetadata
                    .stringValue(SqlQueryBuilderUtils.ANN_JOIN_TABLE, "name")
                    .orElseGet(() ->
                        namingStrategy.mappedName(association));
                String joinTableSchema = annotationMetadata.stringValue(SqlQueryBuilderUtils.ANN_JOIN_TABLE, SqlMembers.SCHEMA).orElse(null);
                if (!StringUtils.isNotEmpty(joinTableSchema)) {
                    joinTableSchema = schema;
                }
                List<PersistentPropertyPath> leftProperties = new ArrayList<>();
                List<PersistentPropertyPath> rightProperties = new ArrayList<>();
                boolean isAssociationOwner = inverseSide.isEmpty();
                List<String> leftJoinTableColumns = SqlQueryBuilderUtils.resolveJoinTableJoinColumns(annotationMetadata,
                    isAssociationOwner, entity, namingStrategy);
                List<String> rightJoinTableColumns = SqlQueryBuilderUtils.resolveJoinTableJoinColumns(annotationMetadata,
                    !isAssociationOwner, association.getAssociatedEntity(), namingStrategy);
                PersistentProperty property2 = entity.getIdentity();
                PersistentEntityUtils.traversePersistentProperties(Collections.emptyList(), property2, (associations1, property3)
                    -> leftProperties.add(PersistentPropertyPath.of(associations1, property3, "")));
                PersistentProperty property1 = associatedEntity.getIdentity();
                PersistentEntityUtils.traversePersistentProperties(Collections.emptyList(), property1, (associations, property)
                    -> rightProperties.add(PersistentPropertyPath.of(associations, property, "")));
                if (leftJoinTableColumns.size() == leftProperties.size()) {
                    for (int i = 0; i < leftJoinTableColumns.size(); i++) {
                        PersistentPropertyPath pp = leftProperties.get(i);
                        String columnName = leftJoinTableColumns.get(i);
                        // TODO: Should we treat join table fields as primary keys?
                        columns.add(getColumnDefinition(pp.getProperty(), columnName, false, true, true));
                    }
                } else {
                    for (PersistentPropertyPath pp : leftProperties) {
                        String columnName = namingStrategy.mappedJoinTableColumn(entity, pp.getAssociations(), pp.getProperty());
                        columns.add(getColumnDefinition(pp.getProperty(), columnName, false, true, true));
                    }
                }
                if (rightJoinTableColumns.size() == rightProperties.size()) {
                    for (int i = 0; i < rightJoinTableColumns.size(); i++) {
                        PersistentPropertyPath pp = rightProperties.get(i);
                        String columnName = rightJoinTableColumns.get(i);
                        columns.add(getColumnDefinition(pp.getProperty(), columnName, false, true, true));
                    }
                } else {
                    for (PersistentPropertyPath pp : rightProperties) {
                        String columnName = namingStrategy.mappedJoinTableColumn(entity, pp.getAssociations(), pp.getProperty());
                        columns.add(getColumnDefinition(pp.getProperty(), columnName, false, true, true));
                    }
                }
                SqlTableMapping joinTable = new SqlTableMapping(joinTableSchema, joinTableName, escape, SqlTableMapping.TableType.JOIN, null, columns);
                tables.add(joinTable);
            }
        }

        List<PersistentProperty> identities = entity.getIdentityProperties();
        List<SqlColumnMapping> primaryKeyColumns = getPrimaryKeyColumns(identities, namingStrategy);

        List<SqlColumnMapping> columns = new ArrayList<>();
        List<SqlCheckConstraint> checks = new ArrayList<>();

        PersistentProperty version = entity.getVersion();
        if (version != null && !version.isGenerated()) {
            String columnName = namingStrategy.mappedName(Collections.emptyList(), version);
            boolean required = isRequired(Collections.emptyList(), version);
            SqlColumnMapping column = getColumnDefinition(version, columnName, false, required, false);
            columns.add(column);
            deriveNumericChecks(checks, tableName, columnName, required, version);
        }

        BiConsumer<List<Association>, PersistentProperty> addColumn = (associations, property) -> {
            String columnName = namingStrategy.mappedName(associations, property);
            boolean required = isRequired(associations, property);
            SqlColumnMapping column = getColumnDefinition(property, columnName, false, required,
                !SqlQueryBuilderUtils.isNotForeign(associations));
            columns.add(column);
            deriveNumericChecks(checks, tableName, columnName, required, property);
        };

        for (PersistentProperty prop : entity.getPersistentProperties()) {
            PersistentEntityUtils.traversePersistentProperties(Collections.emptyList(), prop, addColumn);
        }

        List<SqlSequenceMapping> sequences = getSqlSequenceMappings(identities);
        List<SqlIndexMapping> indexes = getSqlIndexMappings(entity);

        // Derive numeric checks for identity properties as well
        for (PersistentProperty identity : identities) {
            List<PersistentPropertyPath> ids = new ArrayList<>();
            PersistentEntityUtils.traversePersistentProperties(Collections.emptyList(), identity, (associations, property)
                -> ids.add(PersistentPropertyPath.of(associations, property, "")));
            for (PersistentPropertyPath pp : ids) {
                String colName = namingStrategy.mappedName(pp.getAssociations(), pp.getProperty());
                boolean required = isRequired(pp.getAssociations(), pp.getProperty());
                deriveNumericChecks(checks, tableName, colName, required, pp.getProperty());
            }
        }

        SqlTableMapping table = new SqlTableMapping(schema, tableName, escape, SqlTableMapping.TableType.MAIN, primaryKeyColumns, columns, sequences,
            indexes, checks.isEmpty() ? null : checks);
        tables.add(table);
        return tables;
    }

    /**
     * Creates a new Column object based on the provided PersistentProperty and other mapped field attributes.
     *
     * @param prop         the PersistentProperty to create the Column for
     * @param column       the name of the column
     * @param primaryKey   whether the column is a primary key
     * @param required     whether the column is required
     * @param isForeign    whether the column is a foreign key
     * @return             a new Column object representing the provided PersistentProperty
     * @throws IllegalStateException if the provided property is an Association
     * @throws MappingException      if the data type of the property is unknown
     */
    private static SqlColumnMapping getColumnDefinition(PersistentProperty prop, String column, boolean primaryKey, boolean required,
                                                        boolean isForeign) {
        if (prop instanceof Association) {
            throw new IllegalStateException("Association is not supported here");
        }
        AnnotationMetadata annotationMetadata = prop.getAnnotationMetadata();
        String definition = annotationMetadata.stringValue(MappedProperty.class, "definition").orElse(null);
        DataType dataType = prop.getDataType();
        boolean autoGenerated = !isForeign && prop.isGenerated();
        GeneratedValue.Type generatedValueType = autoGenerated ? prop.getAnnotationMetadata().enumValue(GeneratedValue.class, GeneratedValue.Type.class)
            .orElse(AUTO) : null;
        OptionalInt optPrecision = SqlQueryBuilderUtils.findPersistenceColumnValue(annotationMetadata, "precision");
        OptionalInt optScale = SqlQueryBuilderUtils.findPersistenceColumnValue(annotationMetadata, "scale");

        SqlDbType dbType = getDbType(prop);

        Integer precision = null;
        Integer scale = null;

        return switch (dataType) {
            case STRING -> {
                int stringLength = annotationMetadata.findAnnotation("jakarta.validation.constraints.Size$List")
                    .flatMap(v -> {
                        Optional value = v.getValue(AnnotationValue.class);
                        return (Optional<AnnotationValue<Annotation>>) value;
                    }).map(v -> v.intValue("max"))
                    .orElseGet(() -> SqlQueryBuilderUtils.findPersistenceColumnValue(annotationMetadata, "length"))
                    .orElse(255);

                yield new SqlColumnMapping(column, dataType, dbType, primaryKey, stringLength, required, autoGenerated, generatedValueType, definition);
            }
            case UUID, BOOLEAN, TIMESTAMP, DATE, TIME, LONG, SHORT, BYTE,
                BYTE_ARRAY, STRING_ARRAY, CHARACTER_ARRAY, SHORT_ARRAY, INTEGER_ARRAY,
                LONG_ARRAY, FLOAT_ARRAY, DOUBLE_ARRAY, BOOLEAN_ARRAY -> new SqlColumnMapping(column, dataType, dbType, primaryKey,
                null, required, autoGenerated, generatedValueType, definition);
            case CHARACTER -> new SqlColumnMapping(column, dataType, dbType, primaryKey, 1, required, autoGenerated, generatedValueType, definition);
            case JSON -> new SqlColumnMapping(column, dataType, dbType, primaryKey, null, null, null, required, autoGenerated, generatedValueType,
                definition, prop.getJsonDataType());
            case INTEGER -> {
                if (optPrecision.isPresent()) {
                    // TODO: Does precision make sense for integer
                    precision = optPrecision.getAsInt();
                }
                yield new SqlColumnMapping(column, dataType, dbType, primaryKey, null, precision, required, autoGenerated, generatedValueType,
                    definition);
            }
            case BIGDECIMAL, FLOAT, DOUBLE -> {
                // TODO: Should only BigDecimal support precision and scale (like Hibernate?)
                if (optPrecision.isPresent()) {
                    precision = optPrecision.getAsInt();
                }
                if (optScale.isPresent()) {
                    scale = optScale.getAsInt();
                }
                yield new SqlColumnMapping(column, dataType, dbType, primaryKey, null, precision, scale, required, autoGenerated, generatedValueType,
                    definition, null);
            }
            default -> {
                if (StringUtils.isNotEmpty(definition)) {
                    yield new SqlColumnMapping(column, dataType, dbType, primaryKey, null, required, autoGenerated, generatedValueType, definition);
                }
                throw new MappingException("Unable to create table column for property [" + prop.getName() + "] of entity [" + prop.getOwner().getName() + "] with unknown data type: " + dataType);
            }
        };
    }

    /**
     * Returns the database type corresponding to the given persistent property.
     *
     * @param property the persistent property
     * @return the database type
     * @throws IllegalStateException if the property is an association
     * @throws MappingException if the data type of the property is unknown
     */
    private static SqlDbType getDbType(PersistentProperty property) {
        DataType dataType = property.getDataType();

        return switch (dataType) {
            case STRING -> SqlDbType.VARCHAR;
            case UUID -> SqlDbType.UUID;
            case BOOLEAN -> SqlDbType.BOOLEAN;
            case TIMESTAMP -> SqlDbType.TIMESTAMP;
            case DATE -> SqlDbType.DATE;
            case TIME -> SqlDbType.TIME;
            case LONG -> SqlDbType.BIGINT;
            case CHARACTER -> SqlDbType.CHAR;
            case INTEGER -> SqlDbType.INTEGER;
            case BIGDECIMAL -> SqlDbType.NUMERIC;
            case FLOAT -> SqlDbType.FLOAT;
            case BYTE_ARRAY -> SqlDbType.BINARY;
            case DOUBLE -> SqlDbType.DOUBLE;
            case SHORT, BYTE -> SqlDbType.SMALLINT;
            case JSON -> SqlDbType.JSON;
            case STRING_ARRAY, CHARACTER_ARRAY, SHORT_ARRAY, INTEGER_ARRAY,
                LONG_ARRAY, FLOAT_ARRAY, DOUBLE_ARRAY, BOOLEAN_ARRAY -> SqlDbType.ARRAY;
            default -> {
                if (property.isEnum()) {
                    yield SqlDbType.ENUM;
                } else if (property.isAssignable(Clob.class)) {
                    yield SqlDbType.CLOB;
                } else if (property.isAssignable(Blob.class)) {
                    yield SqlDbType.BLOB;
                } else {
                    throw new MappingException("Unable to create table column for property [" + property.getName() + "] of entity [" + property.getOwner().getName() + "] with unknown data type: " + dataType);
                }
            }
        };
    }

    /**
     * Determines whether a property is required based on its associations and own requirements.
     *
     * This method checks the associations of the given property and returns false if any of them are not required.
     * If there are no associations or all associations are required, it then checks the requirement status of the property itself.
     * If a foreign association exists, its requirement status takes precedence over the property's own requirement status.
     *
     * @param associations the associations of the property
     * @param property the property to check
     * @return true if the property is required, false otherwise
     */
    private static boolean isRequired(List<Association> associations, PersistentProperty property) {
        Association foreignAssociation = null;
        for (Association association : associations) {
            if (!association.isRequired()) {
                return false;
            }
            if (association.getKind() != Relation.Kind.EMBEDDED && foreignAssociation == null) {
                foreignAssociation = association;
            }
        }
        if (foreignAssociation != null) {
            return foreignAssociation.isRequired();
        }
        return property.isRequired();
    }

    private static List<SqlSequenceMapping> getSqlSequenceMappings(List<PersistentProperty> identities) {
        List<SqlSequenceMapping> sequences = new ArrayList<>();
        for (PersistentProperty identity : identities) {
            if (identity.isGenerated()) {
                GeneratedValue.Type idGeneratorType = identity.getAnnotationMetadata()
                    .enumValue(GeneratedValue.class, GeneratedValue.Type.class)
                    .orElse(null);
                final String generatedDefinition = identity.getAnnotationMetadata().stringValue(GeneratedValue.class, "definition").orElse(null);
                final String definedSequenceName = identity.getAnnotationMetadata().stringValue(GeneratedValue.class, "ref").orElse(null);
                sequences.add(new SqlSequenceMapping(generatedDefinition, definedSequenceName, identity.getDataType(), Optional.ofNullable(idGeneratorType)));
            }
        }
        return sequences;
    }

    private static List<SqlIndexMapping> getSqlIndexMappings(PersistentEntity entity) {
        List<SqlIndexMapping> indexMappings = new ArrayList<>();
        final Optional<List<AnnotationValue<Index>>> indexes = entity
            .findAnnotation(Indexes.class)
            .map(idxes -> idxes.getAnnotations(VALUE_MEMBER, Index.class));

        Stream.of(indexes)
            .flatMap(Optional::stream)
            .flatMap(Collection::stream)
            .forEach(index -> {
                String name = index.stringValue("name").orElse("");
                boolean unique = index.booleanValue("unique").orElse(false);
                String[] columns = index.stringValues("columns");
                indexMappings.add(new SqlIndexMapping(name, unique, columns));
            });
        return indexMappings;
    }

    private static List<SqlColumnMapping> getPrimaryKeyColumns(List<PersistentProperty> identities, NamingStrategy namingStrategy) {
        List<SqlColumnMapping> primaryKeyColumns = new ArrayList<>(identities.size());
        for (PersistentProperty identity : identities) {
            List<PersistentPropertyPath> ids = new ArrayList<>();
            PersistentEntityUtils.traversePersistentProperties(Collections.emptyList(), identity, (associations, property)
                -> ids.add(PersistentPropertyPath.of(associations, property, "")));
            for (PersistentPropertyPath pp : ids) {
                String columnName = namingStrategy.mappedName(pp.getAssociations(), pp.getProperty());
                SqlColumnMapping column = getColumnDefinition(pp.getProperty(), columnName, true,
                    isRequired(pp.getAssociations(), pp.getProperty()), !SqlQueryBuilderUtils.isNotForeign(pp.getAssociations()));
                primaryKeyColumns.add(column);
            }
        }
        return primaryKeyColumns;
    }

    private static boolean isNumeric(DataType dt) {
        return dt.isNumeric();
    }

    /**
     * Checks presence of a constraint annotation, accounting for repeatable containers (e.g. X$List).
     */
    private static boolean hasConstraint(AnnotationMetadata am, String annotationName) {
        return hasConstraint0(am, annotationName) || hasConstraint0(am, toJavax(annotationName));
    }

    private static boolean hasConstraint0(AnnotationMetadata am, String ann) {
        return am.hasAnnotation(ann) || am.findAnnotation(ann + "$List").isPresent();
    }

    private static String toJavax(String ann) {
        return ann.startsWith("jakarta.validation.") ? ann.replace("jakarta.validation.", "javax.validation.") : ann;
    }

    /**
     * Resolve the first occurrence of an annotation either as a direct annotation or via its repeatable container ($List).
     */
    private static Optional<AnnotationValue<Annotation>> findConstraintAnnotation(AnnotationMetadata am, String baseAnnotationName) {
        // Try jakarta first
        Optional<AnnotationValue<Annotation>> direct = am.findAnnotation(baseAnnotationName);
        if (direct.isPresent()) {
            return direct;
        }
        Optional<AnnotationValue<Annotation>> fromList = am.findAnnotation(baseAnnotationName + "$List")
            .flatMap(SqlSchemaUtils::firstContained);
        if (fromList.isPresent()) {
            return fromList;
        }
        // Fallback to javax
        String alt = toJavax(baseAnnotationName);
        Optional<AnnotationValue<Annotation>> directAlt = am.findAnnotation(alt);
        if (directAlt.isPresent()) {
            return directAlt;
        }
        Optional<AnnotationValue<Annotation>> fromListAlt = am.findAnnotation(alt + "$List")
            .flatMap(SqlSchemaUtils::firstContained);
        if (fromListAlt.isPresent()) {
            return fromListAlt;
        }
        // As a last resort, scan all present annotation names to find any matching simple name (handles unusual storage)
        try {
            Set<String> names = am.getAnnotationNames();
            String simple = baseAnnotationName.substring(baseAnnotationName.lastIndexOf('.') + 1);
            Optional<String> any = names.stream()
                .filter(n -> n.endsWith("." + simple) || n.endsWith("." + simple + "$List"))
                .findFirst();
            if (any.isPresent()) {
                String found = any.get();
                if (found.endsWith("$List")) {
                    return am.findAnnotation(found)
                        .flatMap(v -> {
                            Optional value = v.getValue(AnnotationValue.class);
                            return (Optional<AnnotationValue<Annotation>>) value;
                        });
                } else {
                    return am.findAnnotation(found);
                }
            }
        } catch (Throwable ignored) {
            // Some AnnotationMetadata impls may not support name enumeration; ignore and return empty
        }
        return Optional.empty();
    }

     /**
      * Return the first contained annotation from a repeatable container annotation value.
      * - "value" attribute as AnnotationValue[]
      * - enumerating annotations under VALUE_MEMBER
      * - falling back to the container itself (some impls squash singletons)
      */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static Optional<AnnotationValue<Annotation>> firstContained(AnnotationValue<?> container) {
        try {
            Optional<AnnotationValue[]> arr = container.get("value", AnnotationValue[].class);
            if (arr.isPresent()) {
                AnnotationValue[] avs = arr.get();
                if (avs != null && avs.length > 0) {
                    return (Optional) Optional.of(avs[0]);
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            var list = container.getAnnotations(VALUE_MEMBER, Annotation.class);
            if (list != null && !list.isEmpty()) {
                return (Optional) list.stream().findFirst();
            }
        } catch (Throwable ignored) {
        }
        try {
            Optional v = container.getValue(AnnotationValue.class);
            if (v.isPresent()) {
                return (Optional) v;
            }
        } catch (Throwable ignored) {
        }
        return Optional.empty();
    }

    /**
     * Resolve a numeric member from an annotation which may appear directly or in its repeatable container.
     */
    private static OptionalLong getFirstLongValue(AnnotationMetadata am, String baseAnnotationName, String member) {
        // Resolve via unified finder (handles direct + $List + javax fallback)
        Optional<AnnotationValue<Annotation>> ann = findConstraintAnnotation(am, baseAnnotationName);
        if (ann.isPresent()) {
            return ann.get().longValue(member);
        }
        return OptionalLong.empty();
    }

    /**
     * Resolve first integral value (int/long) from annotation, supporting direct and $List containers,
     * and both jakarta and javax namespaces.
     */
    private static Optional<String> getFirstIntegralString(AnnotationMetadata am, String baseAnnotationName, String member) {
        Optional<AnnotationValue<Annotation>> av = findConstraintAnnotation(am, baseAnnotationName);
        if (av.isPresent()) {
            Optional<String> s = extractMemberAsString(av.get(), member);
            if (s.isPresent()) {
                return s;
            }
        }
        String alt = toJavax(baseAnnotationName);
        if (!alt.equals(baseAnnotationName)) {
            Optional<AnnotationValue<Annotation>> av2 = findConstraintAnnotation(am, alt);
            if (av2.isPresent()) {
                Optional<String> s2 = extractMemberAsString(av2.get(), member);
                if (s2.isPresent()) {
                    return s2;
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Extract a member value from an AnnotationValue as a String, trying long/int/string/object coercions.
     */
    private static Optional<String> extractMemberAsString(AnnotationValue<?> av, String member) {
        // Try numeric accessors
        OptionalLong l = av.longValue(member);
        if (l.isPresent()) {
            return Optional.of(String.valueOf(l.getAsLong()));
        }
        OptionalInt i = av.intValue(member);
        if (i.isPresent()) {
            return Optional.of(String.valueOf(i.getAsInt()));
        }
        // Try direct string
        Optional<String> s = av.stringValue(member);
        if (s.isPresent()) {
            return s;
        }
        // Try generic typed extraction
        try {
            Optional<Long> ol = av.get(member, Long.class);
            if (ol.isPresent()) {
                return Optional.of(String.valueOf(ol.get()));
            }
            Optional<Integer> oi = av.get(member, Integer.class);
            if (oi.isPresent()) {
                return Optional.of(String.valueOf(oi.get()));
            }
            Optional<Object> oo = av.get(member, Object.class);
            if (oo.isPresent()) {
                return Optional.of(String.valueOf(oo.get()));
            }
        } catch (Throwable ignored) {
        }
        return Optional.empty();
    }

    private static void deriveNumericChecks(List<SqlCheckConstraint> out, String table, String column, boolean required, PersistentProperty property) {
        if (!isNumeric(property.getDataType())) {
            return;
        }
        AnnotationMetadata am = property.getAnnotationMetadata();
        // DEBUG: temporary logging to diagnose missing Min/Max/Decimal* annotations in tests


        // Positive family
        if (hasConstraint(am, "jakarta.validation.constraints.Positive")) {
            addCheck(out, table, column, required, ">", "0");
        }
        if (hasConstraint(am, "jakarta.validation.constraints.PositiveOrZero")) {
            addCheck(out, table, column, required, ">=", "0");
        }
        if (hasConstraint(am, "jakarta.validation.constraints.Negative")) {
            addCheck(out, table, column, required, "<", "0");
        }
        if (hasConstraint(am, "jakarta.validation.constraints.NegativeOrZero")) {
            addCheck(out, table, column, required, "<=", "0");
        }

        // Min/Max (support direct + $List and jakarta/javax, read int/long)
        Optional<String> minStr = getFirstIntegralString(am, "jakarta.validation.constraints.Min", "value");

        minStr.ifPresent(v -> addCheck(out, table, column, required, ">=", v));
        Optional<String> maxStr = getFirstIntegralString(am, "jakarta.validation.constraints.Max", "value");

        maxStr.ifPresent(v -> addCheck(out, table, column, required, "<=", v));

        // DecimalMin/DecimalMax (support direct + $List and jakarta/javax)
        Optional<AnnotationValue<Annotation>> decMin = am.findAnnotation("jakarta.validation.constraints.DecimalMin");
        if (decMin.isEmpty()) {
            decMin = am.findAnnotation("jakarta.validation.constraints.DecimalMin$List")
                .flatMap(SqlSchemaUtils::firstContained);
        }
        if (decMin.isEmpty()) {
            decMin = am.findAnnotation("javax.validation.constraints.DecimalMin");
            if (decMin.isEmpty()) {
                decMin = am.findAnnotation("javax.validation.constraints.DecimalMin$List")
                    .flatMap(SqlSchemaUtils::firstContained);
            }
        }
        decMin.ifPresent(a -> {
            String v = extractMemberAsString(a, "value").orElse("0");
            boolean inclusive = a.booleanValue("inclusive").orElse(true);

            addCheck(out, table, column, required, inclusive ? ">=" : ">", v);
        });

        Optional<AnnotationValue<Annotation>> decMax = am.findAnnotation("jakarta.validation.constraints.DecimalMax");
        if (decMax.isEmpty()) {
            decMax = am.findAnnotation("jakarta.validation.constraints.DecimalMax$List")
                .flatMap(SqlSchemaUtils::firstContained);
        }
        if (decMax.isEmpty()) {
            decMax = am.findAnnotation("javax.validation.constraints.DecimalMax");
            if (decMax.isEmpty()) {
                decMax = am.findAnnotation("javax.validation.constraints.DecimalMax$List")
                    .flatMap(SqlSchemaUtils::firstContained);
            }
        }
        decMax.ifPresent(a -> {
            String v = extractMemberAsString(a, "value").orElse("0");
            boolean inclusive = a.booleanValue("inclusive").orElse(true);

            addCheck(out, table, column, required, inclusive ? "<=" : "<", v);
        });
    }

    private static void addCheck(List<SqlCheckConstraint> out, String table, String column, boolean required, String op, String value) {
        String name = "ck_" + sanitize(table) + "_" + sanitize(column) + "_" + opToken(op) + "_" + sanitize(value);
        // basic length safety
        if (name.length() > 63) {
            name = name.substring(0, 63);
        }
        out.add(new SqlCheckConstraint(name, column, op, value, !required));
    }

    private static String sanitize(String s) {
        if (s == null) {
            return "x";
        }
        String t = s.toLowerCase(java.util.Locale.ENGLISH).replaceAll("[^a-z0-9]", "_");
        t = t.replaceAll("_+", "_");
        if (t.startsWith("_")) {
            t = t.substring(1);
        }
        if (t.endsWith("_")) {
            t = t.substring(0, t.length() - 1);
        }
        return t.isEmpty() ? "x" : t;
    }

    private static String opToken(String op) {
        return switch (op) {
            case ">" -> "gt";
            case ">=" -> "ge";
            case "<" -> "lt";
            case "<=" -> "le";
            default -> "op";
        };
    }
}
