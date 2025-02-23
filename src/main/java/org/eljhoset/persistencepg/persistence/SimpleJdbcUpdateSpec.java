package org.eljhoset.persistencepg.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
class SimpleJdbcUpdateSpec implements UpdatableJdbcClient.UpdateSpec {
    private final String tableName;
    private final JdbcClient jdbcClient;
    private final Map<String, Object> params = new HashMap<>();
    String whereClause;
    Map<String, Object> whereColumns;

    public SimpleJdbcUpdateSpec param(String name, Object value) {
        params.put(name, value);
        return this;
    }

    @Override
    public UpdatableJdbcClient.UpdateSpec setNull(String name) {
        params.put(name, null);
        return this;
    }

    @Override
    public UpdatableJdbcClient.UpdateSpec where(String where, Map<String, Object> whereColumns) {
        this.whereClause = where;
        this.whereColumns = whereColumns;
        return this;
    }

    public int execute() {
        if (params.isEmpty()) {
            throw new IllegalStateException("No non-ID columns were set for update. "
                                            + "At least one non-ID column must be present in 'params'.");
        }
        if (whereColumns.isEmpty()) {
            throw new IllegalStateException("No ID columns found");
        }
        String setClause = params.keySet().stream()
                .map(col -> "%s = :%s".formatted(col, col))
                .collect(Collectors.joining(", "));

        String sql = "UPDATE %s SET %s WHERE %s".formatted(tableName, setClause, whereClause);

        var statementSpec = jdbcClient.sql(sql);

        params.forEach(statementSpec::param);
        whereColumns.forEach(statementSpec::param);
        return statementSpec.update();
    }
}
