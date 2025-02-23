package org.eljhoset.persistencepg.persistence;

import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;
import org.springframework.core.convert.ConversionService;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.lang.NonNull;

import java.util.Map;

@RequiredArgsConstructor
public class ConversionAwareUpdatableJdbcClient implements UpdatableJdbcClient {
    @Delegate
    private final JdbcClient delegate;
    private final ConversionService conversionService;
    @Override
    public @NonNull ConversionAwareStatementSpec sql(@NonNull String sql) {
        return new ConversionAwareStatementSpec(delegate.sql(sql), conversionService);
    }

    public @NonNull ConversionAwareJdbcClientInsertSpec insert(@NonNull String tableName) {
        SimpleJdbcInsertSpec simpleJdbcInsertSpec = new SimpleJdbcInsertSpec(tableName, this);
        return new ConversionAwareJdbcClientInsertSpec(simpleJdbcInsertSpec, conversionService);
    }

    public @NonNull UpdateSpec update(@NonNull String tableName) {
        return new SimpleJdbcUpdateSpec(tableName, this);
    }

    @RequiredArgsConstructor
    public static class ConversionAwareJdbcClientInsertSpec implements InsertSpec {
        @Delegate
        private final InsertSpec delegate;
        private final ConversionService conversionService;

        public InsertSpec compositeParam(Object object) {
            if (object == null) return this;
            if (object instanceof Map<?, ?> map) {
                map.forEach((key, value) -> param(key.toString(), value));
                return this;
            }
            if (!conversionService.canConvert(object.getClass(), Map.class)) return this;
            Map<?, ?> map = conversionService.convert(object, Map.class);
            if (map == null) return this;
            map.forEach((key, value) -> param(key.toString(), value));
            return this;
        }
    }

}
