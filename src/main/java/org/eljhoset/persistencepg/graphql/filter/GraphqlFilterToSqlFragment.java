package org.eljhoset.persistencepg.graphql.filter;

import lombok.experimental.UtilityClass;
import org.eljhoset.persistencepg.graphql.metadata.model.Metadata;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@UtilityClass
public class GraphqlFilterToSqlFragment {

    /**
     * Builds the full SQL WHERE clause from the provided filter map.
     * Returns an empty string if the map is null or empty.
     *
     * @param root  the root entity name
     * @param where a map representing the filtering conditions
     * @return a valid SQL WHERE clause or empty string if no conditions
     */
    public SqlFragment buildWhereClause(String root, Map<String, Object> where, Collection<Metadata.Relation> relations) {
        if (where == null || where.isEmpty()) {
            return SqlFragment.empty();
        }
        SqlFragmentBuilder sqlFragmentBuilder = SqlFragmentBuilder.newInstance();
        Map<String, Metadata.Relation> relationMap = relations.stream().collect(Collectors.toMap(r -> "%s_%s".formatted(r.table(), r.name()), Function.identity()));
        ConditionBuilder conditionBuilder = ConditionBuilder.newInstance(relationMap);
        Condition condition = conditionBuilder.buildFrom(root, where);
        return sqlFragmentBuilder.toSql(root, condition);
    }
}
