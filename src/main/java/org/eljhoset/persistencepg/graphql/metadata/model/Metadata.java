package org.eljhoset.persistencepg.graphql.metadata.model;

import java.util.List;

public record Metadata(List<Table> tables, List<Relation> relations) {
    public record Relation(String table, String name, String column, String refTable, String refColumn, Type type) {
        public enum Type {
            ONE_TO_MANY, MANY_TO_ONE
        }
    }
}
