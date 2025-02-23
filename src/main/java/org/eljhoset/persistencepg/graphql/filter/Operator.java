package org.eljhoset.persistencepg.graphql.filter;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

sealed interface Operator {
    @Getter
    @RequiredArgsConstructor
    enum OperatorType {
        EQ("_eq"), NEQ("_neq"), LIKE("_like"), IN("_in"), IS_NULL("_is_null"), GT("_gt"), LT("_lt"), GTE("_gte"), LTE("_lte");
        private final String key;
        static OperatorType fromKey(String key) {
            return Stream.of(values())
                    .filter(type -> type.getKey().equals(key))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Operator not found for key: " + key));
        }
        static boolean isOperator(String key) {
            return Stream.of(OperatorType.values()).anyMatch(type -> type.getKey().equals(key));
        }
    }

    static Collection<Operator> fromMap(Map<?, ?> map) {
        return map.entrySet().stream()
                .map(filter -> {
                    String key = filter.getKey().toString();
                    if (!OperatorType.isOperator(key)) {
                        return new Noop();
                    }
                    return switch (OperatorType.fromKey(key)) {
                        case EQ -> new Eq(filter.getValue());
                        case NEQ -> new Neq(filter.getValue());
                        case LIKE -> new Like(filter.getValue());
                        case IN -> in(filter);
                        case IS_NULL -> new IsNull(Boolean.parseBoolean(filter.getValue().toString()));
                        case GT -> new Gt(filter.getValue());
                        case LT -> new Lt(filter.getValue());
                        case GTE -> new Gte(filter.getValue());
                        case LTE -> new Lte(filter.getValue());
                    };
                }).toList();
    }

    record Eq(Object value) implements Operator { }
    record Neq(Object value) implements Operator { }
    record Like(Object value) implements Operator { }
    record In(List<?> values) implements Operator { }
    record IsNull(Boolean value) implements Operator { }
    record Gt(Object value) implements Operator { }
    record Lt(Object value) implements Operator { }
    record Gte(Object value) implements Operator { }
    record Lte(Object value) implements Operator { }
    record Noop() implements Operator { }

    private static Operator in(Map.Entry<?, ?> filter) {
        if (filter.getValue() instanceof List<?> values) {
            return new In(values);
        }
        return new Noop();
    }

}
