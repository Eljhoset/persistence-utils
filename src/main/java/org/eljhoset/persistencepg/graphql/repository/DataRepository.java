package org.eljhoset.persistencepg.graphql.repository;

import lombok.RequiredArgsConstructor;
import org.eljhoset.persistencepg.graphql.filter.SqlFragment;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class DataRepository {
    private final JdbcClient jdbcClient;

    public List<Map<String, Object>> genericTableData(String tableName, SqlFragment sqlFragment) {
        if (!sqlFragment.isEmpty()) {
            sqlFragment = sqlFragment.prepend(" WHERE ");
        }
        StringBuilder sql = new StringBuilder("SELECT %s.* FROM %s AS %s".formatted(tableName, tableName, tableName));
        sqlFragment.joins().forEach(join -> sql.append(appendLn(join)));
        sql.append(sqlFragment.sql());
        JdbcClient.StatementSpec spec = jdbcClient.sql(sql.toString());
        sqlFragment.params().forEach(spec::param);
        return spec.query(new ColumnMapRowMapper())
                .list();
    }

    public Collection<Map<String, Object>> genericTableDataByColumn(String tableName, String column, Collection<Object> values, SqlFragment sqlFragment) {
        if (!sqlFragment.isEmpty()) {
            sqlFragment = sqlFragment.prepend(" AND ");
        }
        StringBuilder sql = new StringBuilder("SELECT %s.* FROM %s AS %s".formatted(tableName, tableName, tableName));
        sqlFragment.joins().forEach(join -> sql.append(appendLn(join)));
        sql.append(" WHERE %s.%s IN (:values) %s".formatted(tableName, column, sqlFragment.sql()));
        sql.append(appendLn(sqlFragment.sql()));

        JdbcClient.StatementSpec spec = jdbcClient.sql(sql.toString())
                .param("values", values);
        sqlFragment.params().forEach(spec::param);
        return spec.query(new ColumnMapRowMapper())
                .list();
    }

    private static String appendLn(String join) {
        return "%s %s".formatted(System.lineSeparator(), join);
    }
}

