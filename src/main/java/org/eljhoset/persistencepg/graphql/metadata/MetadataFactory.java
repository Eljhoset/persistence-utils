package org.eljhoset.persistencepg.graphql.metadata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import graphql.schema.GraphQLScalarType;
import lombok.experimental.UtilityClass;
import org.eljhoset.persistencepg.graphql.metadata.deserializer.GraphQLScalarTypeJsonDeserializer;
import org.eljhoset.persistencepg.graphql.metadata.deserializer.RelationTypeDeserializer;
import org.eljhoset.persistencepg.graphql.metadata.model.Metadata;

import java.io.File;
import java.io.IOException;

@UtilityClass
public class MetadataFactory {
    public Metadata fromFile(String pathname) throws IOException {
        ObjectMapper objectMapper = objectMapper();
        return objectMapper.readValue(new File(pathname), Metadata.class);
    }

    public Metadata fromContent(String content) throws IOException {
        ObjectMapper objectMapper = objectMapper();
        return objectMapper.readValue(content, Metadata.class);
    }

    private ObjectMapper objectMapper() {
        SimpleModule module = new SimpleModule();
        module.addDeserializer(GraphQLScalarType.class, new GraphQLScalarTypeJsonDeserializer());
        module.addDeserializer(Metadata.Relation.Type.class, new RelationTypeDeserializer());
        return new ObjectMapper(new YAMLFactory())
                .registerModule(module)
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    }
}
