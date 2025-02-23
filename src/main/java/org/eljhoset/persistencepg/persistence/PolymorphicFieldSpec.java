package org.eljhoset.persistencepg.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.ConversionService;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.lang.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public class PolymorphicFieldSpec {
    private final JdbcClient.StatementSpec statementSpec;
    private final ConversionService conversionService;
    private final List<PolymorphicFieldSpecBuilder<?>> fields = new ArrayList<>();

    public static <T> PolymorphicFieldSpecBuilder<T> newInstance(JdbcClient.StatementSpec statementSpec, ConversionService conversionService, String field, String discriminatorColumn) {
        PolymorphicFieldSpec polymorphicFieldSpec = new PolymorphicFieldSpec(statementSpec, conversionService);
        return new PolymorphicFieldSpecBuilder<>(field, discriminatorColumn, polymorphicFieldSpec);
    }

    public <T> @NonNull JdbcClient.MappedQuerySpec<T> query(@NonNull Class<T> resultType) {
        RowMapper<T> rowMapper = (rs, rowNum) -> {
            Map<String, Class<?>> typeMap = new HashMap<>();
            for (PolymorphicFieldSpecBuilder<?> field : fields) {
                Object discriminatorValue = rs.getObject(field.discriminatorColumn);
                Class<?> aClass = field.fieldMapping.get(field.field).get(discriminatorValue);
                typeMap.put(field.field, aClass);
            }
            return NestedRowMapper.newInstance(resultType, conversionService, typeMap).mapRow(rs, rowNum);
        };
        return statementSpec.query(rowMapper);
    }

    @RequiredArgsConstructor
    public static class PolymorphicFieldSpecBuilder<T> {
        private final String field;
        private final String discriminatorColumn;
        private final PolymorphicFieldSpec spec;
        final Map<String, Map<Object, Class<? extends T>>> fieldMapping = new HashMap<>();

        public PolymorphicFieldSpecBuilder<T> when(Object discriminatorValue, Class<? extends T> type) {
            fieldMapping.computeIfAbsent(field, k -> new HashMap<>()).put(discriminatorValue, type);
            return this;
        }

        public <R> PolymorphicFieldSpecBuilder<R> columnDiscriminator(String field, String discriminatorColumn) {
            spec.fields.add(this);
            return new PolymorphicFieldSpecBuilder<>(field, discriminatorColumn, spec);
        }

        public <R> @NonNull JdbcClient.MappedQuerySpec<R> query(@NonNull Class<R> resultType) {
            spec.fields.add(this);
            return spec.query(resultType);
        }
    }




}
