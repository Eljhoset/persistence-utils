package org.eljhoset.persistencepg.graphql;

import graphql.schema.GraphQLSchema;
import graphql.schema.idl.SchemaPrinter;
import org.eljhoset.persistencepg.graphql.metadata.MetadataFactory;
import org.eljhoset.persistencepg.graphql.metadata.model.Metadata;
import org.eljhoset.persistencepg.graphql.repository.DataRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.graphql.GraphQlSourceBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.graphql.execution.BatchLoaderRegistry;
import org.springframework.graphql.execution.GraphQlSource;

import java.io.IOException;

@Configuration
public class GraphQLConfig {

    @Bean
    Metadata metadata(@Value("${graphql.metadata.url:src/main/resources/graphql/metadata.yml}")
                      String pathname) throws IOException {
        return MetadataFactory.fromFile(pathname);
    }

    @Bean
    GraphQLSchema graphQLSchema(Metadata metadata, BatchLoaderRegistry batchLoaderRegistry, DataRepository dataRepository) {
        GraphQLSchema.Builder builder = MetadataToGraphQLSchemaBuilder.from(metadata);
        MetadataToGraphQLCodeRegistry codeRegistry = MetadataToGraphQLCodeRegistry.newInstance(batchLoaderRegistry, dataRepository);
        return builder.codeRegistry(codeRegistry.convert(metadata)).build();
    }

    @Bean
    public GraphQlSourceBuilderCustomizer buildCustomizer(GraphQLSchema schema) {
        SchemaPrinter printer = new SchemaPrinter();
        String sdl = printer.print(schema);
        System.out.println(sdl);
        ByteArrayResource schemaResource = new ByteArrayResource(sdl.getBytes());
        return (GraphQlSource.SchemaResourceBuilder builder) ->
                builder.configureGraphQl(e -> e.schema(schema))
                        .schemaResources(schemaResource);
    }

}
