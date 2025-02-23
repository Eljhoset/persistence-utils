package org.eljhoset.persistencepg.graphql.metadata.deserializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import graphql.Scalars;
import graphql.schema.GraphQLScalarType;

import java.io.IOException;

public class GraphQLScalarTypeJsonDeserializer extends JsonDeserializer<GraphQLScalarType> {
    @Override
    public GraphQLScalarType deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
        return getScalarType(p.getText());
    }

    private static GraphQLScalarType getScalarType(String type) {
        return switch (type) {
            case "ID" -> Scalars.GraphQLID;
            case "Int" -> Scalars.GraphQLInt;
            case "String" -> Scalars.GraphQLString;
            case "Float" -> Scalars.GraphQLFloat;
            case "Boolean" -> Scalars.GraphQLBoolean;
            default -> throw new IllegalArgumentException("Unknown type: " + type);
        };
    }
}
