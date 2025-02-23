package org.eljhoset.persistencepg.graphql.filter;

import graphql.Scalars;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLList;
import lombok.experimental.UtilityClass;

@UtilityClass
public class GraphqlFilterTypes {
    public final GraphQLInputObjectType stringFilterType = GraphQLInputObjectType.newInputObject()
            .name("StringFilter")
            .field(GraphQLInputObjectField.newInputObjectField().name("_eq").type(Scalars.GraphQLString))
            .field(GraphQLInputObjectField.newInputObjectField().name("_neq").type(Scalars.GraphQLString))
            .field(GraphQLInputObjectField.newInputObjectField().name("_like").type(Scalars.GraphQLString))
            .field(GraphQLInputObjectField.newInputObjectField().name("_in").type(new GraphQLList(Scalars.GraphQLString)))
            .field(GraphQLInputObjectField.newInputObjectField().name("_is_null").type(Scalars.GraphQLBoolean))
            .build();
    public final GraphQLInputObjectType floatFilterType = GraphQLInputObjectType.newInputObject()
                    .name("FloatFilter")
                    .field(GraphQLInputObjectField.newInputObjectField().name("_eq").type(Scalars.GraphQLFloat))
                    .field(GraphQLInputObjectField.newInputObjectField().name("_neq").type(Scalars.GraphQLFloat))
                    .field(GraphQLInputObjectField.newInputObjectField().name("_gt").type(Scalars.GraphQLFloat))
                    .field(GraphQLInputObjectField.newInputObjectField().name("_lt").type(Scalars.GraphQLFloat))
                    .field(GraphQLInputObjectField.newInputObjectField().name("_gte").type(Scalars.GraphQLFloat))
                    .field(GraphQLInputObjectField.newInputObjectField().name("_lte").type(Scalars.GraphQLFloat))
                    .field(GraphQLInputObjectField.newInputObjectField().name("_in").type(new GraphQLList(Scalars.GraphQLFloat)))
                    .field(GraphQLInputObjectField.newInputObjectField().name("_is_null").type(Scalars.GraphQLBoolean))
                    .build();
    public final GraphQLInputObjectType intFilterType = GraphQLInputObjectType.newInputObject()
                            .name("IntFilter")
                            .field(GraphQLInputObjectField.newInputObjectField().name("_eq").type(Scalars.GraphQLInt))
                            .field(GraphQLInputObjectField.newInputObjectField().name("_neq").type(Scalars.GraphQLInt))
                            .field(GraphQLInputObjectField.newInputObjectField().name("_gt").type(Scalars.GraphQLInt))
                            .field(GraphQLInputObjectField.newInputObjectField().name("_lt").type(Scalars.GraphQLInt))
                            .field(GraphQLInputObjectField.newInputObjectField().name("_gte").type(Scalars.GraphQLInt))
                            .field(GraphQLInputObjectField.newInputObjectField().name("_lte").type(Scalars.GraphQLInt))
                            .field(GraphQLInputObjectField.newInputObjectField().name("_in").type(new GraphQLList(Scalars.GraphQLInt)))
                            .field(GraphQLInputObjectField.newInputObjectField().name("_is_null").type(Scalars.GraphQLBoolean))
                            .build();
    public final GraphQLInputObjectType idFilterType = GraphQLInputObjectType.newInputObject()
                                    .name("IDFilter")
                                    .field(GraphQLInputObjectField.newInputObjectField().name("_eq").type(Scalars.GraphQLID))
                                    .field(GraphQLInputObjectField.newInputObjectField().name("_neq").type(Scalars.GraphQLID))
                                    .field(GraphQLInputObjectField.newInputObjectField().name("_in").type(new GraphQLList(Scalars.GraphQLID)))
                                    .field(GraphQLInputObjectField.newInputObjectField().name("_is_null").type(Scalars.GraphQLBoolean))
                                    .build();
    public final GraphQLInputObjectType booleanFilterType = GraphQLInputObjectType.newInputObject()
                                            .name("BooleanFilter")
                                            .field(GraphQLInputObjectField.newInputObjectField().name("_eq").type(Scalars.GraphQLBoolean))
                                            .field(GraphQLInputObjectField.newInputObjectField().name("_neq").type(Scalars.GraphQLBoolean))
                                            .field(GraphQLInputObjectField.newInputObjectField().name("_is_null").type(Scalars.GraphQLBoolean))
                                            .build();
}
