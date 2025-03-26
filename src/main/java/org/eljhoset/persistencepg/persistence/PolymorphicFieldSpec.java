package org.eljhoset.persistencepg.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.ConversionService;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.lang.NonNull;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public class PolymorphicFieldSpec {
    private final JdbcClient.StatementSpec statementSpec;
    private final ConversionService conversionService;
    private final List<PolymorphicField<?>> fields = new ArrayList<>();

    public static <T> PolymorphicFieldSpecQueryBuilder<T> newInstance(JdbcClient.StatementSpec statementSpec, ConversionService conversionService, String field, String discriminatorColumn) {
        PolymorphicFieldSpec polymorphicFieldSpec = new PolymorphicFieldSpec(statementSpec, conversionService);
        return polymorphicFieldSpec.new PolymorphicFieldSpecQueryBuilder<>(field, discriminatorColumn);
    }
    public static <T> PolymorphicSpec<T> newInstance(JdbcClient.StatementSpec statementSpec, ConversionService conversionService, String discriminatorColumn) {
        PolymorphicFieldSpec polymorphicFieldSpec = new PolymorphicFieldSpec(statementSpec, conversionService);
        return polymorphicFieldSpec.new PolymorphicSpec<>(discriminatorColumn);
    }

    public <T> @NonNull JdbcClient.MappedQuerySpec<T> query(@NonNull Class<T> resultType) {
        RowMapper<T> rowMapper = (rs, rowNum) -> {
            Map<String, Class<?>> typeMap = getTypeMap(rs);
            return NestedRowMapper.newInstance(resultType, conversionService, typeMap).mapRow(rs, rowNum);
        };
        return statementSpec.query(rowMapper);
    }

    private Map<String, Class<?>> getTypeMap(ResultSet rs) throws SQLException {
        Map<String, Class<?>> typeMap = new HashMap<>();
        for (PolymorphicField<?> field : fields) {
            Object discriminatorValue = rs.getObject(field.discriminatorColumn);
            Class<?> aClass = field.fieldMapping.get(field.field).get(discriminatorValue);
            typeMap.put(field.field, aClass);
        }
        return typeMap;
    }

    record PolymorphicField<T>(String field, String discriminatorColumn,
                               Map<String, Map<Object, Class<? extends T>>> fieldMapping) { }
    @RequiredArgsConstructor
    public class PolymorphicSpec<T> {
        private final String discriminatorColumn;
        private final Map<String, Class<? extends T>> discriminatorMapping = new HashMap<>();

        public PolymorphicSpec<T> when(String discriminatorValue, Class<? extends T> type) {
            discriminatorMapping.put(discriminatorValue, type);
            return this;
        }
        public <R> PolymorphicFieldSpecBuilder<T, R> columnDiscriminator(String field, String discriminatorColumn) {
            return new PolymorphicFieldSpecBuilder<>(this, field, discriminatorColumn);
        }
        public @NonNull JdbcClient.MappedQuerySpec<T> query() {
            RowMapper<T> rowMapper = (rs, rowNum) -> {
                Map<String, Class<?>> typeMap = getTypeMap(rs);
                String discriminatorValue = rs.getString(discriminatorColumn);
                Class<? extends T> type = discriminatorMapping.get(discriminatorValue);
                RowMapper<? extends T> delegateRowMapper = NestedRowMapper.newInstance(type, conversionService, typeMap);
                return delegateRowMapper.mapRow(rs, rowNum);
            };
            return statementSpec.query(rowMapper);
        }
    }
    @RequiredArgsConstructor
    class PolymorphicFieldSpecBuilderDelegate<T> {
        private final String field;
        private final String discriminatorColumn;
        final Map<String, Map<Object, Class<? extends T>>> fieldMapping = new HashMap<>();

        public void when(Object discriminatorValue, Class<? extends T> type) {
            fieldMapping.computeIfAbsent(field, k -> new HashMap<>()).put(discriminatorValue, type);
        }
        void flush() {
            flush(field, discriminatorColumn);
        }
        private void flush(String field, String discriminatorColumn) {
            fields.add(new PolymorphicField<>(field, discriminatorColumn, fieldMapping));
        }
    }
    public class PolymorphicFieldSpecBuilder<T, W> {
        private final PolymorphicSpec<T> spec;
        private final PolymorphicFieldSpecBuilderDelegate<W> delegate;
        PolymorphicFieldSpecBuilder(PolymorphicSpec<T> spec, String field, String discriminatorColumn) {
            this.delegate = new PolymorphicFieldSpecBuilderDelegate<>(field, discriminatorColumn);
            this.spec = spec;
        }
        public PolymorphicFieldSpecBuilder<T, W> when(Object discriminatorValue, Class<? extends W> type) {
            delegate.when(discriminatorValue, type);
            return this;
        }
        public <R> PolymorphicFieldSpecBuilder<T, R> columnDiscriminator(String field, String discriminatorColumn) {
            delegate.flush();
            return new PolymorphicFieldSpecBuilder<>(spec, field, discriminatorColumn);
        }

        public @NonNull JdbcClient.MappedQuerySpec<T> query() {
            delegate.flush();
            return spec.query();
        }
    }
    public class PolymorphicFieldSpecQueryBuilder<T> {
        private final PolymorphicFieldSpecBuilderDelegate<T> delegate;
        PolymorphicFieldSpecQueryBuilder(String field, String discriminatorColumn) {
            this.delegate = new PolymorphicFieldSpecBuilderDelegate<>(field, discriminatorColumn);
        }
        public PolymorphicFieldSpecQueryBuilder<T> when(Object discriminatorValue, Class<? extends T> type) {
            delegate.when(discriminatorValue, type);
            return this;
        }
        public <R> PolymorphicFieldSpecQueryBuilder<R> columnDiscriminator(String field, String discriminatorColumn) {
            delegate.flush();
            return new PolymorphicFieldSpecQueryBuilder<>(field, discriminatorColumn);
        }
        public <R> @NonNull JdbcClient.MappedQuerySpec<R> query(@NonNull Class<R> resultType) {
            delegate.flush();
            return PolymorphicFieldSpec.this.query(resultType);
        }
    }
}

