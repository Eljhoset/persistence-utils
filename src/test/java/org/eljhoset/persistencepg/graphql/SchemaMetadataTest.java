package org.eljhoset.persistencepg.graphql;

import graphql.Scalars;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import graphql.schema.idl.SchemaPrinter;
import org.eljhoset.persistencepg.graphql.metadata.MetadataFactory;
import org.eljhoset.persistencepg.graphql.metadata.model.Metadata;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class SchemaMetadataTest {
    @Test
    void readMetadata() throws Exception {
        //language=yaml
        String yml = """
                tables:
                  - name: books
                    columns:
                      - name: id
                        type: ID
                        filterable: true
                      - name: title
                        type: String
                        filterable: true
                      - name: author_id
                        type: Int
                        filterable: true
                  - name: authors
                    columns:
                      - name: id
                        type: ID
                        filterable: true
                      - name: name
                        filterable: true
                        type: String
                relations:
                  - type: many_to_one
                    table: books
                    name: author
                    column: author_id
                    ref_table: authors
                    ref_column: id
                  - type: one_to_many
                    table: authors
                    name: books
                    column: id
                    ref_table: books
                    ref_column: author_id
                """;
        Metadata metadata = MetadataFactory.fromContent(yml);
        System.out.println(metadata);
        assertThat(metadata.tables())
                .hasSize(2)
                .satisfiesExactly(
                        books -> assertThat(books)
                                .satisfies(
                                        table -> assertThat(table.name()).isEqualTo("books"),
                                        table -> assertThat(table.columns())
                                                .hasSize(3)
                                                .extracting("name", "type", "filterable")
                                                .containsExactly(
                                                        tuple("id", Scalars.GraphQLID, true),
                                                        tuple("title", Scalars.GraphQLString, true),
                                                        tuple("author_id", Scalars.GraphQLInt, true)
                                                )
                                ),
                        authors -> assertThat(authors)
                                .satisfies(
                                        table -> assertThat(table.name()).isEqualTo("authors"),
                                        table -> assertThat(table.columns())
                                                .hasSize(2)
                                                .extracting("name", "type")
                                                .containsExactly(
                                                        tuple("id", Scalars.GraphQLID),
                                                        tuple("name", Scalars.GraphQLString)
                                                )
                                )
                );
        assertThat(metadata.relations())
                .hasSize(2)
                .extracting("type", "table", "name", "column", "refTable", "refColumn")
                .contains(
                        tuple(Metadata.Relation.Type.MANY_TO_ONE, "books", "author", "author_id", "authors", "id"),
                        tuple(Metadata.Relation.Type.ONE_TO_MANY, "authors", "books", "id", "books", "author_id")
                );

        GraphQLSchema graphQLSchema = MetadataToGraphQLSchemaBuilder.from(metadata).build();
        SchemaPrinter printer = new SchemaPrinter();
        GraphQLType query = graphQLSchema.getType("Query");
        assertThat(printer.print(query))
                .isEqualTo("""
                        type Query {
                          authors(where: AuthorsFilter): [Authors]
                          books(where: BooksFilter): [Books]
                        }
                        """);
        assertThat(printer.print(graphQLSchema.getType("Authors")))
                .isEqualTo("""
                        type Authors {
                          books(where: BooksFilter): [Books]
                          id: ID
                          name: String
                        }
                        """);
        assertThat(printer.print(graphQLSchema.getType("AuthorsFilter")))
                .isEqualTo("""
                        input AuthorsFilter {
                          _and: [AuthorsFilter]
                          _not: AuthorsFilter
                          _or: [AuthorsFilter]
                          books: BooksFilter
                          id: IDFilter
                          name: StringFilter
                        }
                        """);
        assertThat(printer.print(graphQLSchema.getType("Books")))
                .isEqualTo("""
                        type Books {
                          author: Authors
                          author_id: Int
                          id: ID
                          title: String
                        }
                        """);
        assertThat(printer.print(graphQLSchema.getType("BooksFilter")))
                .isEqualTo("""
                        input BooksFilter {
                          _and: [BooksFilter]
                          _not: BooksFilter
                          _or: [BooksFilter]
                          author: AuthorsFilter
                          author_id: IntFilter
                          id: IDFilter
                          title: StringFilter
                        }
                        """);
        String sdl = printer.print(graphQLSchema);
        System.out.println(sdl);
    }
}
