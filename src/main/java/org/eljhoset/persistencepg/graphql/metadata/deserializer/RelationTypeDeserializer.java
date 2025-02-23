package org.eljhoset.persistencepg.graphql.metadata.deserializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import org.eljhoset.persistencepg.graphql.metadata.model.Metadata;

import java.io.IOException;

public class RelationTypeDeserializer extends JsonDeserializer<Metadata.Relation.Type> {
    @Override
    public Metadata.Relation.Type deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
        return Metadata.Relation.Type.valueOf(p.getText().toUpperCase());
    }
}
