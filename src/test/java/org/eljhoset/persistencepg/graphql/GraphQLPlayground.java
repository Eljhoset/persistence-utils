package org.eljhoset.persistencepg.graphql;

import graphql.ExecutionInput;
import graphql.GraphQL;
import graphql.Scalars;
import graphql.schema.*;
import graphql.schema.idl.SchemaPrinter;
import org.dataloader.BatchLoader;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderFactory;
import org.dataloader.DataLoaderRegistry;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public class GraphQLPlayground {
    public static void main(String[] args) {
        record Author(Long id, String name) { }
        record Book(Long id, String title, Long authorId) { }

        DataLoaderRegistry registry = new DataLoaderRegistry();
        GraphQLObjectType authorType = GraphQLObjectType.newObject().name("Author")
                .field(f -> f.name("id").type(Scalars.GraphQLID))
                .field(f -> f.name("name").type(Scalars.GraphQLString))
                .field(f -> f.name("books").type(GraphQLList.list(GraphQLTypeReference.typeRef("Book"))))
                .build();

        GraphQLObjectType bookType = GraphQLObjectType.newObject().name("Book")
                .field(f -> f.name("id").type(Scalars.GraphQLID))
                .field(f -> f.name("title").type(Scalars.GraphQLString))
                .field(f -> f.name("author").type(authorType))
                .build();


        GraphQLInputObjectType authorsFilter = GraphQLInputObjectType.newInputObject()
                .name("AuthorsFilter")
                .field(f -> f.name("id").type(Scalars.GraphQLID))
                .field(f -> f.name("name").type(Scalars.GraphQLString))
                .build();
        GraphQLInputObjectType booksFilter = GraphQLInputObjectType.newInputObject()
                .name("BooksFilter")
                .field(f -> f.name("id").type(Scalars.GraphQLID))
                .field(f -> f.name("title").type(Scalars.GraphQLString))
                .field(f -> f.name("author").type(authorsFilter))
                .build();

        GraphQLObjectType queryType = GraphQLObjectType.newObject().name("Query")
                .field(f -> f.name("authors").type(GraphQLList.list(authorType)).argument(a -> a.name("where").type(authorsFilter)))
                .field(f -> f.name("books").type(GraphQLList.list(bookType)).argument(a -> a.name("where").type(booksFilter)))
                .build();

        Map<Long, Author> authors = Map.of(
                1L, new Author(1L, "Author 1"),
                2L, new Author(2L, "Author 2"),
                3L, new Author(3L, "Author 3"),
                4L, new Author(4L, "Author 4")
        );
        List<Book> bookList = List.of(
                new Book(1L, "Book 1", 1L),
                new Book(2L, "Book 2", 2L),
                new Book(3L, "Book 3", 3L)
        );
        Map<Long, Book> booksByAuthor = bookList.stream().collect(Collectors.toMap(Book::authorId, Function.identity()));

        DataFetcher<Collection<Author>> authorsDataFetcher = environment -> authors.values();
        BatchLoader<Long, Author> authorBatchLoader = keys -> CompletableFuture.supplyAsync(() -> {
            System.out.println("Fetching authors: " + keys);
            return keys.stream().map(authors::get).toList();
        });
        BatchLoader<Long, Book> booksBatchLoader = keys -> CompletableFuture.supplyAsync(() -> {
            System.out.println("Fetching books for authors: " + keys);
            List<Book> list = keys.stream()
                    .map(booksByAuthor::get)
                    .filter(Objects::nonNull)
                    .toList();
            System.out.println("Books: " + list);
            return list;
        });
        DataFetcher<CompletableFuture<Author>> authorDataFetcher = environment -> {
            Book book = environment.getSource();
            assert book != null;
            System.out.println("Fetching author for book: " + book);
            DataLoader<Long, Author> dataLoader = environment.getDataLoader("book_author");
            assert dataLoader != null;
            return dataLoader.load(book.authorId());
        };
        DataFetcher<List<Book>> booksDataFetcher = _ -> bookList;
        DataFetcher<CompletableFuture<Book>> booksByAuthorDataFetcher = environment -> {
            Author author = environment.getSource();
            assert author != null;
            System.out.println("Fetching books for author: " + author);
            DataLoader<Long, Book> dataLoader = environment.getDataLoader("author_books");
            assert dataLoader != null;
            return dataLoader.load(author.id());
        };

        GraphQLCodeRegistry codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
                .dataFetcher(FieldCoordinates.coordinates(queryType, "authors"), authorsDataFetcher)
                .dataFetcher(FieldCoordinates.coordinates(queryType, "books"), booksDataFetcher)
                .dataFetcher(FieldCoordinates.coordinates(bookType, "author"), authorDataFetcher)
                .dataFetcher(FieldCoordinates.coordinates(authorType, "books"), booksByAuthorDataFetcher)
                .build();

        registry.register("book_author", DataLoaderFactory.newDataLoader(authorBatchLoader));
        registry.register("author_books", DataLoaderFactory.newDataLoader(booksBatchLoader));

        GraphQLSchema graphQLSchema = GraphQLSchema.newSchema()
                .query(queryType)
                .codeRegistry(codeRegistry)
                .build();
        SchemaPrinter printer = new SchemaPrinter();
        String sdl = printer.print(graphQLSchema);
        System.out.println(sdl);
        GraphQL ql = GraphQL.newGraphQL(graphQLSchema)
                .build();


        String query =
                """
                        query {
                            books {
                                title
                                author {
                                    name
                                }
                            }
                        }
                        """;

        ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                .query(query)
                .dataLoaderRegistry(registry)
                .build();
        Map<String, Object> books = ql.execute(executionInput).getData();
        System.out.println(books);
        assertThat(books).contains(Map.entry("books", List.of(
                Map.of("title", "Book 1", "author", Map.of("name", "Author 1")),
                Map.of("title", "Book 2", "author", Map.of("name", "Author 2")),
                Map.of("title", "Book 3", "author", Map.of("name", "Author 3"))
        )));

        String authorsQuery =
                """
                        query {
                            authors(where: {id: 1}) {
                                name
                            }
                        }
                        """;
        executionInput = ExecutionInput.newExecutionInput()
                .query(authorsQuery)
                .dataLoaderRegistry(registry)
                .build();
        Map<String, Object> authorsResult = ql.execute(executionInput).getData();
        System.out.println(authorsResult);

        executionInput = ExecutionInput.newExecutionInput()
                .query("""
                        query {
                            authors {
                                name
                                books {
                                    title
                                }
                            }
                        }
                        """)
                .dataLoaderRegistry(registry)
                .build();
        Map<String, Object> authorsWithBooks = ql.execute(executionInput).getData();
        System.out.println(authorsWithBooks);
    }
}
