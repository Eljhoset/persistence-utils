package org.eljhoset.persistencepg.graphql;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLCodeRegistry;
import lombok.RequiredArgsConstructor;
import org.dataloader.BatchLoaderEnvironment;
import org.dataloader.DataLoader;
import org.eljhoset.persistencepg.graphql.filter.GraphqlFilterToSqlFragment;
import org.eljhoset.persistencepg.graphql.filter.SqlFragment;
import org.eljhoset.persistencepg.graphql.metadata.model.Metadata;
import org.eljhoset.persistencepg.graphql.metadata.model.Table;
import org.eljhoset.persistencepg.graphql.repository.DataRepository;
import org.springframework.core.convert.converter.Converter;
import org.springframework.graphql.execution.BatchLoaderRegistry;
import org.springframework.lang.NonNull;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Collectors;


@RequiredArgsConstructor(staticName = "newInstance")
public class MetadataToGraphQLCodeRegistry implements Converter<Metadata, GraphQLCodeRegistry> {
    public static final String WHERE = "where";
    private final BatchLoaderRegistry batchLoaderRegistry;
    private final DataRepository dataRepository;

    public GraphQLCodeRegistry convert(@NonNull Metadata metadata) {
        Collection<Table> tables = metadata.tables();
        Collection<Metadata.Relation> relations = metadata.relations();

        GraphQLCodeRegistry.Builder codeRegistry = GraphQLCodeRegistry.newCodeRegistry();
        tables.forEach(table -> {
            DataFetcher<List<Map<String, Object>>> dataFetcher = environment -> {
                Map<String, Object> arguments = environment.getArguments();
                Map<String, Object> where = getWhere(arguments);
                SqlFragment fragment = GraphqlFilterToSqlFragment.buildWhereClause(table.name(), where, relations);
                return dataRepository.genericTableData(table.name(), fragment);
            };
            codeRegistry.dataFetcher(
                    FieldCoordinates.coordinates("Query", table.name()),
                    dataFetcher
            );
        });
        relations.forEach(relation -> {
            String dataLoaderName = relation.table() + "_" + relation.refTable();
            BiFunction<List<Object>, BatchLoaderEnvironment, Flux<Object>> batchLoader = (ids, environment) -> {
                DataFetchingEnvironment dataFetchingEnvironment = obtainDfeFromEnvironment(environment);
                Map<String, Object> arguments = dataFetchingEnvironment.getArguments();
                Map<String, Object> where = getWhere(arguments);
                SqlFragment condition = GraphqlFilterToSqlFragment.buildWhereClause(relation.refTable(), where, relations);
                Collection<Map<String, Object>> records = dataRepository.genericTableDataByColumn(
                        relation.refTable(),
                        relation.refColumn(),
                        ids,
                        condition
                );
                return switch (relation.type()) {
                    case ONE_TO_MANY -> {
                        Map<Object, List<Map<String, Object>>> listMap = records.stream().collect(
                                Collectors.groupingBy(it -> it.get(relation.refColumn()))
                        );
                        List<List<Map<String, Object>>> results = ids.stream()
                                .map(id -> listMap.getOrDefault(id, List.of()))
                                .toList();
                        yield Flux.fromIterable(results);
                    }
                    case MANY_TO_ONE -> Flux.fromIterable(records);
                };
            };
            batchLoaderRegistry.forName(dataLoaderName).registerBatchLoader(batchLoader);
            DataFetcher<Object> dataFetcher = environment -> {
                Map<String, Object> source = environment.getSource();
                Optional<DataLoader<Object, Object>> dataLoader = Optional.ofNullable(environment.getDataLoader(dataLoaderName));
                return Optional.ofNullable(source)
                        .flatMap(map -> Optional.ofNullable(map.get(relation.column())))
                        .flatMap(columnValue -> dataLoader.map(it -> it.load(columnValue, environment)))
                        .orElse(null);
            };
            codeRegistry.dataFetcher(
                    FieldCoordinates.coordinates(StringUtils.capitalize(relation.table()), relation.name()),
                    dataFetcher
            );
        });
        return codeRegistry.build();
    }

    private static DataFetchingEnvironment obtainDfeFromEnvironment(BatchLoaderEnvironment env) {
        return env.getKeyContextsList().stream()
                .filter(DataFetchingEnvironment.class::isInstance)
                .map(DataFetchingEnvironment.class::cast)
                .findFirst()
                .orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> getWhere(Map<String, Object> arguments) {
        return (Map<String, Object>) arguments.getOrDefault(WHERE, Map.of());
    }
}
