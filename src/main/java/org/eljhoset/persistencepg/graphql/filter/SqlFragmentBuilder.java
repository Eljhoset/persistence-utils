package org.eljhoset.persistencepg.graphql.filter;

import lombok.RequiredArgsConstructor;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RequiredArgsConstructor(staticName = "newInstance")
class SqlFragmentBuilder {
    private final Map<String, AtomicInteger> paramsCounter = new HashMap<>();

    SqlFragment toSql(String root, String column, Operator operator, Collection<String> joins) {
        int counter = paramsCounter.computeIfAbsent(column, _ -> new AtomicInteger(1)).getAndIncrement();
        String paramName = "%s_%d".formatted(column, counter);
        return switch (operator) {
            case Operator.Eq(var value) ->
                    new SqlFragment("%s.%s = :%s".formatted(root, column, paramName), Map.of(paramName, value), joins);
            case Operator.Neq(var value) ->
                    new SqlFragment("%s.%s <> :%s".formatted(root, column, paramName), Map.of(paramName, value), joins);
            case Operator.Like(var value) ->
                    new SqlFragment("%s.%s LIKE :%s".formatted(root, column, paramName), Map.of(paramName, value), joins);
            case Operator.In(var values) -> {
                if (values.isEmpty()) {
                    yield SqlFragment.empty();
                }
                yield new SqlFragment("%s.%s IN (:%s)".formatted(root, column, paramName), Map.of(paramName, values), joins);
            }
            case Operator.IsNull(var value) ->
                    new SqlFragment("%s.%s IS %s NULL".formatted(root, column, Boolean.TRUE.equals(value) ? "" : "NOT"), Map.of(), joins);
            case Operator.Gt(var value) ->
                    new SqlFragment("%s.%s > :%s".formatted(root, column, paramName), Map.of(paramName, value), joins);
            case Operator.Lt(var value) ->
                    new SqlFragment("%s.%s < :%s".formatted(root, column, paramName), Map.of(paramName, value), joins);
            case Operator.Gte(var value) ->
                    new SqlFragment("%s.%s >= :%s".formatted(root, column, paramName), Map.of(paramName, value), joins);
            case Operator.Lte(var value) ->
                    new SqlFragment("%s.%s <= :%s".formatted(root, column, paramName), Map.of(paramName, value), joins);
            case Operator.Noop _ -> SqlFragment.empty();
        };
    }

    SqlFragment toSql(String root, Condition condition) {
        return toSql(root, condition, List.of());
    }
    SqlFragment toSql(String root, Condition condition, Collection<String> joins) {
        return switch (condition) {
            case Condition.FieldCondition(var column, var operator) -> toSql(root, column, operator, joins);
            case Condition.And(var conditions) -> subConditions(root, conditions, joins, " AND ", "(", ")");
            case Condition.Or(var conditions) -> subConditions(root, conditions, joins, " OR ", "(", ")");
            case Condition.Not(var c) -> {
                SqlFragment fragment = toSql(root, c, joins);
                yield new SqlFragment("NOT (%s)".formatted(fragment.sql()), fragment.params(), joins);
            }
            case Condition.RelationalCondition(var name, var relation, var c) when relation!=null -> {
                String join = "JOIN %s %s ON %s.%s = %s.%s"
                        .formatted(relation.refTable(), name, relation.table(), relation.column(), name, relation.refColumn());
                Collection<String> list = Stream.concat(joins.stream(), Stream.of(join)).toList();
                yield toSql(name, c, list);
            }
            case Condition.RelationalCondition(var name, _, var c) -> toSql(name, c, joins);
            case Condition.ConditionGroup(var conditions) -> subConditions(root, conditions, joins, " AND ", "", "");
            case Condition.Noop _ -> SqlFragment.empty();
        };
    }

    private SqlFragment subConditions(String root, Collection<Condition> conditions, Collection<String> joins,
                                      String delimiter, CharSequence prefix, CharSequence suffix) {
        List<SqlFragment> fragments = conditions.stream()
                .map(c -> toSql(root, c, joins))
                .filter(Predicate.not(SqlFragment::isEmpty))
                .toList();
        if (fragments.isEmpty()) return SqlFragment.empty();
        String combinedSql = fragments.stream()
                .map(SqlFragment::sql)
                .collect(Collectors.joining(delimiter, prefix, suffix));
        Map<String, Object> combinedParams = fragments.stream()
                .map(SqlFragment::params)
                .flatMap(map -> map.entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        List<String> allJoins = fragments.stream()
                .map(SqlFragment::joins)
                .flatMap(Collection::stream)
                .distinct()
                .toList();
        return new SqlFragment(combinedSql, combinedParams, allJoins);
    }
}
