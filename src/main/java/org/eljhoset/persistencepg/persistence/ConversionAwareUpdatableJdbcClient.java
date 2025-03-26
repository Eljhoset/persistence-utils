package org.eljhoset.persistencepg.persistence;

import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;
import org.springframework.core.convert.ConversionService;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.lang.NonNull;

import javax.sql.DataSource;
import java.util.Map;


public class ConversionAwareUpdatableJdbcClient implements UpdatableJdbcClient {
    @Delegate
    private final JdbcClient delegate;
    private final DataSource dataSource;
    private final ConversionService conversionService;
    private final ConversionOps conversionOps;

    public ConversionAwareUpdatableJdbcClient(DataSource dataSource, ConversionService conversionService) {
        this.delegate = JdbcClient.create(dataSource);
        this.conversionService = conversionService;
        this.dataSource = dataSource;
        this.conversionOps = new ConversionOps(conversionService);
    }

    @Override
    public @NonNull ConversionAwareStatementSpec sql(@NonNull String sql) {
        return new ConversionAwareStatementSpec(sql, delegate, conversionService);
    }

    public @NonNull ConversionAwareJdbcClientInsertSpec insert(@NonNull String tableName) {
        SimpleJdbcInsertSpec simpleJdbcInsertSpec = new SimpleJdbcInsertSpec(tableName, this);
        return new ConversionAwareJdbcClientInsertSpec(simpleJdbcInsertSpec, conversionService);
    }

    public @NonNull UpdateSpec update(@NonNull String tableName) {
        return new SimpleJdbcUpdateSpec(tableName, this);
    }

    @Override
    public BatchUpdateSpec.WhereStep batchUpdate(String tableName) {
        BatchJdbcUpdateSpec batchJdbcUpdateSpec = new BatchJdbcUpdateSpec(tableName, conversionOps, new NamedParameterJdbcTemplate(dataSource));
        return batchJdbcUpdateSpec.new BatchUpdateSpecBuilder();
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
