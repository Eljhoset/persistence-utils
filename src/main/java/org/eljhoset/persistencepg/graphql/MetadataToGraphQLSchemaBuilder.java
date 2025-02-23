package org.eljhoset.persistencepg.graphql;

import graphql.schema.*;
import lombok.experimental.UtilityClass;
import org.eljhoset.persistencepg.graphql.filter.GraphqlFilterTypes;
import org.eljhoset.persistencepg.graphql.metadata.model.Metadata;
import org.eljhoset.persistencepg.graphql.metadata.model.Table;
import org.springframework.lang.NonNull;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@UtilityClass
public class MetadataToGraphQLSchemaBuilder {

    public GraphQLSchema.Builder from(@NonNull Metadata metadata) {
        Tables tables = new Tables(metadata.tables());
        Relations relations = new Relations(metadata.relations());
        Collection<TableType> tableTypes = getTableTypes(tables, relations);
        GraphQLObjectType.Builder queryBuilder = GraphQLObjectType.newObject().name("Query");
        tableTypes.forEach(tableType -> {
            Table table = tableType.table();
            GraphQLObjectType type = tableType.type();
            Optional<GraphQLArgument> filter = getFilter(table, relations);
            queryBuilder.field(f -> {
                f.name(table.name()).type(GraphQLList.list(type));
                filter.ifPresent(f::argument);
                return f;
            });
        });
        return GraphQLSchema.newSchema()
                .query(queryBuilder.build());
    }

    private static Optional<GraphQLArgument> getFilter(Table table, Relations relations) {
        if (table.hasFilterableColumns()) {
            String filterName = getFilterName(table.name());
            GraphQLInputObjectType.Builder filterTypeBuilder = GraphQLInputObjectType.newInputObject().name(filterName);
            table.columns().stream().filter(Table.Column::filterable).forEach(column -> {
                String columnName = column.name();
                GraphQLInputObjectType filterType = switch (column.type().getName()) {
                    case "String" -> GraphqlFilterTypes.stringFilterType;
                    case "Float" -> GraphqlFilterTypes.floatFilterType;
                    case "Int" -> GraphqlFilterTypes.intFilterType;
                    case "ID" -> GraphqlFilterTypes.idFilterType;
                    case "Boolean" -> GraphqlFilterTypes.booleanFilterType;
                    default -> throw new IllegalArgumentException("Unsupported type: " + column.type().getName());
                };
                filterTypeBuilder.field(f -> f.name(columnName).type(filterType));
                relations.ifRelation(table.name(), columnName)
                        .apply(relation ->  {
                            GraphQLTypeReference typeRef = GraphQLTypeReference.typeRef(getFilterName(relation.refTable()));
                            filterTypeBuilder.field(f -> f.name(relation.name()).type(typeRef));
                        });
            });
            GraphQLInputObjectType filterType = filterTypeBuilder
                    .field(GraphQLInputObjectField.newInputObjectField()
                            .name("_not")
                            .type(GraphQLTypeReference.typeRef(filterName)))
                    .field(GraphQLInputObjectField.newInputObjectField()
                            .name("_and")
                            .type(GraphQLList.list(GraphQLTypeReference.typeRef(filterName))))
                    .field(GraphQLInputObjectField.newInputObjectField()
                            .name("_or")
                            .type(GraphQLList.list(GraphQLTypeReference.typeRef(filterName))))
                    .build();
            return Optional.of(
                    GraphQLArgument.newArgument()
                            .name("where")
                            .type(filterType)
                            .build()
            );
        }
        return Optional.empty();
    }

    private static String getFilterName(String name) {
        return StringUtils.capitalize(name) + "Filter";
    }

    private static Collection<TableType> getTableTypes(Tables tables, Relations relations) {
        return tables.stream().map(table -> {
            String capitalizedTableName = StringUtils.capitalize(table.name());
            GraphQLObjectType.Builder tableBuilder = GraphQLObjectType.newObject().name(capitalizedTableName);
            table.columns().forEach(column -> {
                Consumer<Metadata.Relation> relationConsumer = relation -> {
                    GraphQLTypeReference typeRef = GraphQLTypeReference.typeRef(StringUtils.capitalize(relation.refTable()));
                    GraphQLFieldDefinition.Builder typeBuilder = GraphQLFieldDefinition.newFieldDefinition().name(relation.name()).type(typeRef);
                    tableBuilder.field(f -> f.name(column.name()).type(column.type()));
                    if (relation.type() == Metadata.Relation.Type.ONE_TO_MANY) {
                        typeBuilder = typeBuilder.type(GraphQLList.list(typeRef));
                        if (tables.byName(relation.refTable()).filter(Table::hasFilterableColumns).isPresent()) {
                            GraphQLArgument.Builder argument = GraphQLArgument.newArgument().name("where")
                                    .type(GraphQLTypeReference.typeRef(getFilterName(relation.refTable())));
                            typeBuilder.argument(argument);
                        }
                    }
                    tableBuilder.field(typeBuilder);
                };
                Runnable runnable = () -> tableBuilder.field(f -> f.name(column.name()).type(column.type()));
                relations.ifRelation(table.name(), column.name()).apply(relationConsumer, runnable);
            });
            return new TableType(table, tableBuilder.build());
        }).toList();
    }

    private record TableType(Table table, GraphQLObjectType type) { }

    private class Relations implements Iterable<Metadata.Relation> {
        private final Collection<Metadata.Relation> relationsList;
        private final Map<String, Map<String, Metadata.Relation>> relationsByTable;

        private Relations(Collection<Metadata.Relation> relations) {
            this.relationsList = relations;
            relationsByTable = relations.stream()
                    .collect(Collectors.groupingBy(Metadata.Relation::table, Collectors.toMap(Metadata.Relation::column, Function.identity())));
        }

        private Fold ifRelation(String table, String column) {
            return Optional.ofNullable(relationsByTable.get(table))
                    .flatMap(it -> Optional.ofNullable(it.get(column)))
                    .<Fold>map(relation -> (consumer, _) -> consumer.accept(relation))
                    .orElse((_, runnable) -> runnable.run());
        }

        @FunctionalInterface
        private interface Fold {
            void apply(Consumer<Metadata.Relation> consumer, Runnable runnable);
            default void apply(Consumer<Metadata.Relation> consumer) {
                apply(consumer, () -> {});
            }
        }

        @Override
        public @NonNull Iterator<Metadata.Relation> iterator() {
            return relationsList.iterator();
        }
    }

    private class Tables implements Iterable<Table> {
        private final Collection<Table> tablesList;
        private final Map<String, Table> tablesByName;

        private Tables(Collection<Table> tablesList) {
            this.tablesList = tablesList;
            tablesByName = tablesList.stream().collect(Collectors.toMap(Table::name, Function.identity()));
        }

        private Optional<Table> byName(String name) {
            return Optional.ofNullable(tablesByName.get(name));
        }

        private Stream<Table> stream() {
            return tablesList.stream();
        }

        @Override
        public @NonNull Iterator<Table> iterator() {
            return tablesList.iterator();
        }
    }
}
