package org.eljhoset.persistencepg.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.KeyHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
class SimpleJdbcInsertSpec implements UpdatableJdbcClient.InsertSpec {
    private final String tableName;
    private final JdbcClient jdbcClient;
    private final Map<String, Object> params = new HashMap<>();

    public SimpleJdbcInsertSpec param(String name, Object value) {
        params.put(name, value);
        return this;
    }
    public int execute(KeyHolder keyHolder, String... generateColumns) {
        String columns = String.join(", ", params.keySet());
        String values = params.keySet().stream()
                .map(":%s"::formatted)
                .collect(Collectors.joining(", "));
        String sql = "INSERT INTO %s (%s) VALUES (%s)".formatted(tableName, columns, values);
        var statementSpec = jdbcClient.sql(sql);
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            statementSpec.param(entry.getKey(), entry.getValue());
        }
        if (keyHolder != null && ( generateColumns != null && generateColumns.length > 0 )) {
            return statementSpec.update(keyHolder, generateColumns);
        }
        if (keyHolder != null) {
            return statementSpec.update(keyHolder);
        }
        return statementSpec.update();
    }
}
