package org.eljhoset.persistencepg.graphql.metadata.model;

import graphql.schema.GraphQLScalarType;

import java.util.List;

public record Table(String name, List<Column> columns) {
    public boolean hasFilterableColumns() {
        return columns.stream().anyMatch(Column::filterable);
    }
    public record Column(String name, GraphQLScalarType type, boolean filterable) { }
}
