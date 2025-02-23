package org.eljhoset.persistencepg.graphql.filter;

import lombok.With;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
@With
public record SqlFragment(String sql, Map<String, Object> params, Collection<String> joins) {
    public boolean isEmpty() {
        return sql.isEmpty();
    }
    public static SqlFragment empty() {
        return new SqlFragment("", Map.of(), List.of());
    }
    public SqlFragment prepend(String sql) {
        return withSql(sql + this.sql);
    }

    public void forEach(BiConsumer<String, Object> action) {
        params.forEach(action);
    }

}
