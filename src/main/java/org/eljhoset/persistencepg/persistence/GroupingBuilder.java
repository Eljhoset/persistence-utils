package org.eljhoset.persistencepg.persistence;

import lombok.RequiredArgsConstructor;

import java.util.Map;

@RequiredArgsConstructor
public class GroupingBuilder<T> {
    private final String property;
    private final Map<String, String> groupingRules;
    private final T statementSpec;

    public T by(String field) {
        groupingRules.put(property, field);
        return statementSpec;
    }

}
