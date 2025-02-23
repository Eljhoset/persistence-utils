package org.eljhoset.persistencepg.graphql.filter;

import lombok.RequiredArgsConstructor;
import org.eljhoset.persistencepg.graphql.metadata.model.Metadata;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@RequiredArgsConstructor(staticName = "newInstance")
class ConditionBuilder {
    private final Map<String, AtomicInteger> counter = new HashMap<>();
    private final Map<String, Metadata.Relation> relations;

    Condition buildFrom(String root, Object condition) {
        if (condition instanceof Map<?, ?> map) {
            return map.entrySet().stream().map(entry -> {
                String key = entry.getKey().toString();
                Object value = entry.getValue();
                return switch (key) {
                    case "_and" -> and(root, value);
                    case "_or" -> or(root, value);
                    case "_not" -> not(root, value);
                    default -> {
                        String relation = "%s_%s".formatted(root, key);
                        if (relations.containsKey(relation)) {
                            yield getRelationalCondition(key, relation, relations.get(relation), value);
                        }
                        Condition fieldCondition = fieldCondition(key, value);
                        if (fieldCondition instanceof Condition.FieldCondition fc && fc.operator() instanceof Operator.Noop) {
                            yield getRelationalCondition(root, relation, null, value);
                        }
                        yield fieldCondition;
                    }
                };
            }).collect(Collectors.collectingAndThen(Collectors.toList(), Condition.ConditionGroup::new));
        }
        return new Condition.Noop();
    }

    private Condition.RelationalCondition getRelationalCondition(String root, String name, Metadata.Relation relation, Object value) {
        int i = counter.computeIfAbsent(name, _ -> new AtomicInteger()).incrementAndGet();
        return new Condition.RelationalCondition("%s_%d".formatted(name, i), relation, buildFrom(root, value));
    }

    private List<Condition> buildFrom(String root, Collection<?> subConditions) {
        return subConditions.stream().<Condition>mapMulti((subCondition, downstream) -> {
            if (subCondition instanceof Map<?, ?> subMap) {
                downstream.accept(buildFrom(root, subMap));
            }
        }).toList();
    }

    private Condition and(String root, Object value) {
        if (value instanceof Collection<?> subConditions) {
            return new Condition.And(buildFrom(root, subConditions));
        }
        return new Condition.Noop();
    }

    private Condition or(String root, Object value) {
        if (value instanceof Collection<?> subConditions) {
            return new Condition.Or(buildFrom(root, subConditions));
        }
        return new Condition.Noop();
    }

    private Condition not(String root, Object value) {
        return new Condition.Not(buildFrom(root, value));
    }

    private Condition fieldCondition(String field, Object value) {
        if (value instanceof Map<?, ?> opMap) {
            return new Condition.FieldCondition(field, Operator.fromMap(opMap).stream().findFirst().orElseThrow());
        }
        return new Condition.Noop();
    }
}
