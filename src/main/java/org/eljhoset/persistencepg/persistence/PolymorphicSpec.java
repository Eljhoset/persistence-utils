package org.eljhoset.persistencepg.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.ConversionService;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.lang.NonNull;

import java.util.HashMap;
import java.util.Map;

@RequiredArgsConstructor
public class PolymorphicSpec<T> {
    private final ConversionService conversionService;
    private final String discriminatorColumn;
    private final JdbcClient.StatementSpec statementSpec;
    private final Map<String, Class<? extends T>> discriminatorMapping = new HashMap<>();

    public PolymorphicSpec<T> when(String discriminatorValue, Class<? extends T> type) {
        discriminatorMapping.put(discriminatorValue, type);
        return this;
    }

    public @NonNull JdbcClient.MappedQuerySpec<T> query() {
        RowMapper<T> rowMapper = (rs, rowNum) -> {
            String discriminatorValue = rs.getString(discriminatorColumn);
            Class<? extends T> type = discriminatorMapping.get(discriminatorValue);
            RowMapper<? extends T> delegateRowMapper = NestedRowMapper.newInstance(type, this.conversionService);
            return delegateRowMapper.mapRow(rs, rowNum);
        };
        return statementSpec.query(rowMapper);
    }
}
