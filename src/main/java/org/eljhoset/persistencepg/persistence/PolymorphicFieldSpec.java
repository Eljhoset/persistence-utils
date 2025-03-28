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
import java.util.function.Consumer;

@RequiredArgsConstructor
public class PolymorphicFieldSpec {
    private final JdbcClient.StatementSpec statementSpec;
    private final ConversionService conversionService;
    private final List<PolymorphicField<?>> fields = new ArrayList<>();
    private final List<PolymorphicField<?>> nestedFields = new ArrayList<>();


    public void addField(PolymorphicField<?> field){
        fields.add(field);
    }

    public <T> @NonNull JdbcClient.MappedQuerySpec<T> query(@NonNull Class<T> resultType) {
        RowMapper<T> rowMapper = (rs, rowNum) -> {
            TypeResolver typeResolver = getTypeResolver(rs);
            return NestedRowMapper.newInstance(resultType, conversionService, typeResolver).mapRow(rs, rowNum);
        };
        return statementSpec.query(rowMapper);
    }

    private TypeResolver getTypeResolver(ResultSet rs) throws SQLException {
        Map<String, Class<?>> typeMap = getTypeByProperty(rs);
        Map<Class<?>, Class<?>> typeByTypeMap = getTypeByType(typeMap, rs);
        return new TypeResolver(typeByTypeMap, typeMap);
    }

    private Map<String, Class<?>> getTypeByProperty(ResultSet rs) throws SQLException {
        Map<String, Class<?>> typeMap = new HashMap<>();
        for (PolymorphicField<?> field : fields) {
            Object discriminatorValue = rs.getObject(field.discriminatorColumn());
            Class<?> aClass = field.fieldMapping().get(field.field()).get(discriminatorValue);
            typeMap.put(field.field(), aClass);
        }
        return typeMap;
    }
    private Map<Class<?>, Class<?>> getTypeByType(Map<String, Class<?>> typeMap, ResultSet rs) throws SQLException {
        Map<Class<?>, Class<?>> typeByTypeMap = new HashMap<>();
        for (PolymorphicField<?> field : nestedFields) {
            Class<?> base = typeMap.get(field.field());
            if (base==null) continue;
            Object discriminatorValue = rs.getObject(field.discriminatorColumn());
            Class<?> aClass = field.fieldMapping().get(field.field()).get(discriminatorValue);
            if (base.isAssignableFrom(aClass)) {
                typeByTypeMap.put(base, aClass);
            }
        }
        return typeByTypeMap;
    }

    @RequiredArgsConstructor
    public class PolymorphicSpec<T> {
        private final String discriminatorColumn;
        private final Map<String, Class<? extends T>> discriminatorMapping = new HashMap<>();

        public PolymorphicSpec<T> when(String discriminatorValue, Class<? extends T> type) {
            discriminatorMapping.put(discriminatorValue, type);
            return this;
        }
        public <R> PolymorphicFieldSpecBuilder<T, R> columnDiscriminator(String field, String discriminatorColumn) {
            return new PolymorphicFieldSpecBuilder<>(this, field, discriminatorColumn, fields::add);
        }
        public @NonNull JdbcClient.MappedQuerySpec<T> query() {
            RowMapper<T> rowMapper = (rs, rowNum) -> {
                TypeResolver typeResolver = getTypeResolver(rs);
                String discriminatorValue = rs.getString(discriminatorColumn);
                Class<? extends T> type = discriminatorMapping.get(discriminatorValue);
                RowMapper<? extends T> delegateRowMapper = NestedRowMapper.newInstance(type, conversionService, typeResolver);
                return delegateRowMapper.mapRow(rs, rowNum);
            };
            return statementSpec.query(rowMapper);
        }
    }
    @RequiredArgsConstructor
    private static class PolymorphicFieldSpecBuilderDelegate<T> {
        private final String field;
        private final String discriminatorColumn;
        private final Consumer<PolymorphicField<T>> fieldConsumer;
        final Map<String, Map<Object, Class<? extends T>>> fieldMapping = new HashMap<>();

        public void when(Object discriminatorValue, Class<? extends T> type) {
            fieldMapping.computeIfAbsent(field, k -> new HashMap<>()).put(discriminatorValue, type);
        }
        void flush() {
            flush(field, discriminatorColumn);
        }
        private void flush(String field, String discriminatorColumn) {
            fieldConsumer.accept(new PolymorphicField<>(field, discriminatorColumn, fieldMapping));
        }
    }
    public class PolymorphicFieldSpecBuilder<T, W> {
        private final PolymorphicSpec<T> spec;
        private final PolymorphicFieldSpecBuilderDelegate<W> delegate;
        PolymorphicFieldSpecBuilder(PolymorphicSpec<T> spec, String field, String discriminatorColumn, Consumer<PolymorphicField<W>> consumer) {
            this.delegate = new PolymorphicFieldSpecBuilderDelegate<>(field, discriminatorColumn, consumer);
            this.spec = spec;
        }
        public PolymorphicFieldSpecBuilder<T, W> when(Object discriminatorValue, Class<? extends W> type) {
            delegate.when(discriminatorValue, type);
            return this;
        }
        public <R> PolymorphicFieldSpecBuilder<T, R> columnDiscriminator(String field, String discriminatorColumn) {
            delegate.flush();
            return new PolymorphicFieldSpecBuilder<>(spec, field, discriminatorColumn, fields::add);
        }

        public @NonNull JdbcClient.MappedQuerySpec<T> query() {
            delegate.flush();
            return spec.query();
        }
    }
    public class PolymorphicFieldSpecQueryBuilder<T> {
        private final PolymorphicFieldSpecBuilderDelegate<T> delegate;
        PolymorphicFieldSpecQueryBuilder(String field, String discriminatorColumn, Consumer<PolymorphicField<T>> fieldConsumer) {
            this.delegate = new PolymorphicFieldSpecBuilderDelegate<>(field, discriminatorColumn, fieldConsumer);
        }
        public PolymorphicFieldSpecQueryBuilder<T> when(Object discriminatorValue, Class<? extends T> type) {
            delegate.when(discriminatorValue, type);
            return this;
        }
        public <R> PolymorphicFieldSpecQueryBuilder<R> columnDiscriminator(String field, String discriminatorColumn) {
            delegate.flush();
            return new PolymorphicFieldSpecQueryBuilder<>(field, discriminatorColumn, fields::add);
        }
        public <R> PolymorphicFieldSpecQueryBuilder<R> nestedColumnDiscriminator(String field, String discriminatorColumn) {
            delegate.flush();
            return new PolymorphicFieldSpecQueryBuilder<>(field, discriminatorColumn, nestedFields::add);
        }
        public <R> @NonNull JdbcClient.MappedQuerySpec<R> query(@NonNull Class<R> resultType) {
            delegate.flush();
            return PolymorphicFieldSpec.this.query(resultType);
        }
    }
}

