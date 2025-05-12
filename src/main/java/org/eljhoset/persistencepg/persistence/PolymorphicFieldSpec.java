package org.eljhoset.persistencepg.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.ConversionService;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ResultSetExtractor;
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
    private final Map<String, String> groupingRules = new HashMap<>();

    public void addField(PolymorphicField<?> field){
        fields.add(field);
    }

    public <T> @NonNull JdbcClient.MappedQuerySpec<T> query(@NonNull Class<T> resultType) {
        var extractor = new PolymorphicFieldExtractor<>(resultType);
        final List<T> data = statementSpec.query(extractor);
        return new PrePopulatedMappedQuerySpec<>(data);
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

    public GroupingBuilder<PolymorphicFieldSpec> group(String property) {
        return new GroupingBuilder<>(property, groupingRules, this);
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
        @SuppressWarnings("unchecked")
        public @NonNull JdbcClient.MappedQuerySpec<T> query() {
            var extractor = new PolymorphicExtractor<>(discriminatorColumn, discriminatorMapping);
            var data = statementSpec.query(extractor);
            return new PrePopulatedMappedQuerySpec<>((List<T>) data);
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
        public GroupingBuilder<PolymorphicFieldSpecBuilder> group(String property) {
            return new GroupingBuilder<>(property, groupingRules, this);
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
        public GroupingBuilder<PolymorphicFieldSpecQueryBuilder> group(String property) {
            return new GroupingBuilder<>(property, groupingRules, this);
        }
    }
    @RequiredArgsConstructor
    private class PolymorphicFieldExtractor<T> implements ResultSetExtractor<List<T>> {
        private final Class<T> resultType;
        @Override
        public List<T> extractData(@NonNull ResultSet rs) throws SQLException, DataAccessException {
            Map<Integer, TypeResolver> typeResolvers = new HashMap<>();
            var rows = new ArrayList<RowEntry>();
            int index = 0;
            while (rs.next()){
                index++;
                var typeResolver = getTypeResolver(rs);
                typeResolvers.put(index, typeResolver);
                var row = ResultSetUtils.extractColumnValues(index, rs);
                rows.add(row);
            }
            var delegate = new MapperExtractorDelegate<>(resultType, conversionService, rows, typeResolvers, Map.of());
            return delegate.extractData();
        }
    }
    @RequiredArgsConstructor
    private class PolymorphicExtractor<T> implements ResultSetExtractor<List<? extends T>> {
        private final String discriminatorColumn;
        private final Map<String, Class<? extends T>> discriminatorMapping;
        @Override
        public List<? extends T> extractData(@NonNull ResultSet rs) throws SQLException, DataAccessException {
            Map<Integer, Class<? extends T>> types = new HashMap<>();
            var rows = new ArrayList<RowEntry>();
            Map<Integer, TypeResolver> typeResolvers = new HashMap<>();
            int index = 0;
            while (rs.next()){
                index++;
                var typeResolver = getTypeResolver(rs);
                String discriminatorValue = rs.getString(discriminatorColumn);
                Class<? extends T> type = discriminatorMapping.get(discriminatorValue);
                var row = ResultSetUtils.extractColumnValues(index, rs);
                types.put(index, type);
                typeResolvers.put(index, typeResolver);
                rows.add(row);
            }
            return rows.stream().flatMap(rowEntry -> {
               var resultType = types.get(rowEntry.rowNumber());
               var delegate = new MapperExtractorDelegate<>(resultType, conversionService, rows, typeResolvers, Map.of());
               return delegate.extractData().stream();
            }).toList();
        }
    }
}

