package org.eljhoset.persistencepg.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.*;
import java.util.stream.Collectors;

@RequiredArgsConstructor
class BatchJdbcUpdateSpec {
    private final String tableName;
    private final ConversionOps conversionOps;
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final List<RowUpdate> rows = new ArrayList<>();

    public int[] execute(String whereClause, Set<String> idColumns) {
        if (rows.isEmpty()) {
            return new int[0];
        }
        long columns = rows.stream().map(RowUpdate::columns).distinct().count();
        if (columns != 1) {
            throw new IllegalArgumentException("All rows must have the same columns");
        }
        var row = rows.getFirst();
        StringBuilder sqlBuilder = new StringBuilder("UPDATE ");
        sqlBuilder.append(tableName).append(" SET ");
        row.columns().stream()
                .filter(column -> !idColumns.contains(column))
                .forEach(column -> sqlBuilder.append(column).append(" = :").append(column).append(", "));

        // Remove trailing comma and space
        sqlBuilder.setLength(sqlBuilder.length() - 2);
        sqlBuilder.append(" WHERE ").append(whereClause);

        MapSqlParameterSource[] parameterMaps = rows.stream().map(it -> {
            MapSqlParameterSource parameterSource = new MapSqlParameterSource();
            it.params().forEach(parameterSource::addValue);
            return parameterSource;
        }).toArray(MapSqlParameterSource[]::new);

        return jdbcTemplate.batchUpdate(sqlBuilder.toString(), parameterMaps);
    }

    @RequiredArgsConstructor
    class BatchUpdateSpecBuilder implements UpdatableJdbcClient.BatchUpdateSpec.WhereStep {
        @Override
        public  UpdatableJdbcClient.BatchUpdateSpec.ParamStep where(String where, String... idColumns) {
            return new BatchParamStep(where, Arrays.stream(idColumns).collect(Collectors.toSet()));
        }
        @RequiredArgsConstructor
        private class BatchParamStep implements UpdatableJdbcClient.BatchUpdateSpec.ParamStep {
            private final String whereClause;
            private final Set<String> idColumns;
            private final Map<String, Object> params = new HashMap<>();
            @Override
            public UpdatableJdbcClient.BatchUpdateSpec.ParamStep param(String name, Object value) {
                if (value != null) {
                    value = conversionOps.checkAndConvert(value);
                }
                params.put(name, value);
                return this;
            }

            @Override
            public UpdatableJdbcClient.BatchUpdateSpec.ParamStep setNull(String name) {
                params.put(name, null);
                return this;
            }

            @Override
            public UpdatableJdbcClient.BatchUpdateSpec.ParamStep also() {
                addRow();
                return new BatchParamStep(whereClause, idColumns);
            }

            @Override
            public int[] execute() {
                addRow();
                return BatchJdbcUpdateSpec.this.execute(whereClause, idColumns);
            }
            private void addRow() {
                rows.add(new RowUpdate(params));
            }
        }
    }
}
