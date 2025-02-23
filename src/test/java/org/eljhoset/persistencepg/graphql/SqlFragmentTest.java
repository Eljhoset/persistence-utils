package org.eljhoset.persistencepg.graphql;

import org.eljhoset.persistencepg.graphql.filter.GraphqlFilterToSqlFragment;
import org.eljhoset.persistencepg.graphql.filter.SqlFragment;
import org.eljhoset.persistencepg.graphql.metadata.model.Metadata;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class SqlFragmentTest {
    static Stream<Arguments> arguments() {
        return Stream.of(
                Arguments.of(
                        "books",
                        Map.of("state",
                                Map.of("_eq", "REGISTERED")
                        ),
                        "books.state = :state_1",
                        Map.of("state_1", "REGISTERED"),
                        List.of(),
                        List.of()
                ),
                Arguments.of(
                        "books",
                        Map.of("_and",
                                List.of(
                                        Map.of("state",
                                                Map.of("_eq", "REGISTERED")
                                        ),
                                        Map.of("amount",
                                                Map.of("_gt", 100)
                                        )
                                )
                        ),
                        "(books.state = :state_1 AND books.amount > :amount_1)",
                        Map.of("state_1", "REGISTERED", "amount_1", 100),
                        List.of(),
                        List.of()
                ),
                Arguments.of(
                        "books",
                        Map.of("_or",
                                List.of(
                                        Map.of("state",
                                                Map.of("_eq", "REGISTERED")
                                        ),
                                        Map.of("amount",
                                                Map.of("_gt", 100)
                                        )
                                )
                        ),
                        "(books.state = :state_1 OR books.amount > :amount_1)",
                        Map.of("state_1", "REGISTERED", "amount_1", 100),
                        List.of(),
                        List.of()
                ),
                Arguments.of(
                        "books",
                        Map.of("state", Map.of("_neq", "ACTIVE")),
                        "books.state <> :state_1",
                        Map.of("state_1", "ACTIVE"),
                        List.of(),
                        List.of()
                ),
                Arguments.of(
                        "books",
                        Map.of("state", Map.of("_in", List.of("ACTIVE", "REGISTERED"))),
                        "books.state IN (:state_1)",
                        Map.of("state_1", List.of("ACTIVE", "REGISTERED")),
                        List.of(),
                        List.of()
                ),
                Arguments.of(
                        "books",
                        Map.of("amount", Map.of("_in", List.of(10, 20, 30))),
                        "books.amount IN (:amount_1)",
                        Map.of("amount_1", List.of(10, 20, 30)),
                        List.of(),
                        List.of()
                ),
                Arguments.of(
                        "books",
                        Map.of("name", Map.of("_like", "%John%")),
                        "books.name LIKE :name_1",
                        Map.of("name_1", "%John%"),
                        List.of(),
                        List.of()
                ),
                Arguments.of(
                        "books",
                        Map.of("_and",
                                List.of(
                                        Map.of("amount",
                                                Map.of("_gt", 10)
                                        ),
                                        Map.of("amount",
                                                Map.of("_lt", 20)
                                        )
                                )
                        ),
                        "(books.amount > :amount_1 AND books.amount < :amount_2)",
                        Map.of("amount_1", 10, "amount_2", 20),
                        List.of(),
                        List.of()
                ),
                Arguments.of(
                        "books",
                        Map.of("_and", List.of(
                                Map.of("state", Map.of("_eq", "REGISTERED")),
                                Map.of("_or", List.of(
                                        Map.of("amount", Map.of("_gt", 100)),
                                        Map.of("amount", Map.of("_lt", 50))
                                ))
                        )),
                        "(books.state = :state_1 AND (books.amount > :amount_1 OR books.amount < :amount_2))",
                        Map.of("state_1", "REGISTERED", "amount_1", 100, "amount_2", 50),
                        List.of(),
                        List.of()
                ),
                Arguments.of(
                        "books",
                        Map.of("_and",
                                List.of(
                                        Map.of("state",
                                                Map.of("_eq", "REGISTERED")),
                                        Map.of("author",
                                                Map.of("name",
                                                        Map.of("_like", "%John%")
                                                )
                                        )
                                )
                        ),
                        "(books.state = :state_1 AND books_author_1.name LIKE :name_1)",
                        Map.of("state_1", "REGISTERED", "name_1", "%John%"),
                        List.of(),
                        List.of()
                ),
                Arguments.of(
                        "books",
                        Map.of("_and",
                                List.of(
                                        Map.of("state",
                                                Map.of("_eq", "REGISTERED")),
                                        Map.of("author",
                                                Map.of("name",
                                                        Map.of("_like", "%John%")
                                                )
                                        )
                                )
                        ),
                        "(books.state = :state_1 AND books_author_1.name LIKE :name_1)",
                        Map.of("state_1", "REGISTERED", "name_1", "%John%"),
                        List.of(
                                new Metadata.Relation(
                                        "books",
                                        "author",
                                        "author_id",
                                        "authors",
                                        "id",
                                        Metadata.Relation.Type.MANY_TO_ONE
                                )
                        ),
                        List.of("JOIN authors books_author_1 ON books.author_id = books_author_1.id")
                ),
                Arguments.of(
                        "books",
                        Map.of("_and",
                                List.of(
                                        Map.of("state",
                                                Map.of("_eq", "REGISTERED")),
                                        Map.of("author",
                                                Map.of("name",
                                                        Map.of("_like", "%John%")
                                                )
                                        ),
                                        Map.of("prices",
                                                Map.of("active",
                                                        Map.of("_eq", "true")
                                                )
                                        )
                                )
                        ),
                        "(books.state = :state_1 AND books_author_1.name LIKE :name_1 AND books_prices_1.active = :active_1)",
                        Map.of("name_1", "%John%","active_1", "true", "state_1", "REGISTERED"),
                        List.of(
                                new Metadata.Relation(
                                        "books",
                                        "author",
                                        "author_id",
                                        "authors",
                                        "id",
                                        Metadata.Relation.Type.MANY_TO_ONE
                                ),
                                new Metadata.Relation(
                                        "books",
                                        "prices",
                                        "id",
                                        "book_prices",
                                        "book_id",
                                        Metadata.Relation.Type.ONE_TO_MANY
                                )
                        ),
                        List.of(
                                "JOIN authors books_author_1 ON books.author_id = books_author_1.id",
                                "JOIN book_prices books_prices_1 ON books.id = books_prices_1.book_id"
                        )
                ),
                Arguments.of(
                        "authors",
                        Map.of("books", Map.of("_and",
                                List.of(
                                        Map.of("state",
                                                Map.of("_eq", "REGISTERED")),
                                        Map.of("genres",
                                                Map.of("name",
                                                        Map.of("_like", "%Fantasy%")
                                                )
                                        )
                                )
                        )),
                        "(authors_books_1.state = :state_1 AND books_genres_1.name LIKE :name_1)",
                        Map.of("name_1","%Fantasy%", "state_1", "REGISTERED"),
                        List.of(
                                new Metadata.Relation(
                                        "authors",
                                        "books",
                                        "id",
                                        "books",
                                        "author_id",
                                        Metadata.Relation.Type.ONE_TO_MANY
                                ),
                                new Metadata.Relation(
                                        "books",
                                        "genres",
                                        "id",
                                        "books_genres",
                                        "book_id",
                                        Metadata.Relation.Type.ONE_TO_MANY
                                )
                        ),
                        List.of(
                                "JOIN books authors_books_1 ON authors.id = authors_books_1.author_id",
                                "JOIN books_genres books_genres_1 ON books.id = books_genres_1.book_id"
                        )
                ),
                Arguments.of(
                        "authors",
                        Map.of("_and", List.of(
                                Map.of("state", Map.of("_eq", "REGISTERED")),
                                Map.of("books", Map.of("_and",
                                        List.of(
                                                Map.of("state",
                                                        Map.of("_eq", "REGISTERED")),
                                                Map.of("genres",
                                                        Map.of("name",
                                                                Map.of("_like", "%Fantasy%")
                                                        )
                                                )
                                        )
                                ))
                        )),
                        "(authors.state = :state_1 AND (authors_books_1.state = :state_2 AND books_genres_1.name LIKE :name_1))",
                        Map.of("name_1","%Fantasy%", "state_1", "REGISTERED", "state_2","REGISTERED"),
                        List.of(
                                new Metadata.Relation(
                                        "authors",
                                        "books",
                                        "id",
                                        "books",
                                        "author_id",
                                        Metadata.Relation.Type.ONE_TO_MANY
                                ),
                                new Metadata.Relation(
                                        "books",
                                        "genres",
                                        "id",
                                        "books_genres",
                                        "book_id",
                                        Metadata.Relation.Type.ONE_TO_MANY
                                )
                        ),
                        List.of(
                                "JOIN books authors_books_1 ON authors.id = authors_books_1.author_id",
                                "JOIN books_genres books_genres_1 ON books.id = books_genres_1.book_id"
                        )
                ),
                Arguments.of(
                        "authors",
                        Map.of("_and", List.of(
                                Map.of("state", Map.of("_eq", "REGISTERED")),
                                Map.of("books", Map.of("_and",
                                        List.of(
                                                Map.of("state",
                                                        Map.of("_eq", "REGISTERED")),
                                                Map.of("title",
                                                        Map.of("_like", "%Fantasy%")
                                                )
                                        )
                                ))
                        )),
                        "(authors.state = :state_1 AND (authors_books_1.state = :state_2 AND authors_books_1.title LIKE :title_1))",
                        Map.of("state_1", "REGISTERED", "title_1", "%Fantasy%", "state_2", "REGISTERED"),
                        List.of(
                                new Metadata.Relation(
                                        "authors",
                                        "books",
                                        "id",
                                        "books",
                                        "author_id",
                                        Metadata.Relation.Type.ONE_TO_MANY
                                )
                        ),
                        List.of("JOIN books authors_books_1 ON authors.id = authors_books_1.author_id")
                ),
                Arguments.of(
                        "books",
                        Map.of(),
                        "",
                        Map.of(),
                        List.of(),
                        List.of()
                )
        );
    }

    @ParameterizedTest
    @MethodSource("arguments")
    void test(String root,
              Map<String, Object> where,
              String expected,
              Map<String, Object> params,
              Collection<Metadata.Relation> relations,
              Collection<String> joins) {
        SqlFragment whereClause = GraphqlFilterToSqlFragment.buildWhereClause(root, where, relations);
        assertThat(whereClause)
                .extracting(SqlFragment::sql, SqlFragment::params, SqlFragment::joins)
                .containsExactly(expected, params, joins);
    }

}
