package org.eljhoset.persistencepg.graphql.filter;

import org.eljhoset.persistencepg.graphql.metadata.model.Metadata;

import java.util.Collection;

sealed interface Condition {
    record And(Collection<Condition> conditions) implements Condition { }
    record Or(Collection<Condition> conditions) implements Condition { }
    record Not(Condition condition) implements Condition { }
    record FieldCondition(String column, Operator operator) implements Condition { }
    record Noop() implements Condition { }
    record ConditionGroup(Collection<Condition> conditions) implements Condition { }
    record RelationalCondition(String name, Metadata.Relation relation, Condition condition) implements Condition { }
}
