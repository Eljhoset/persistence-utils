package org.eljhoset.persistencepg;

import org.eljhoset.persistencepg.persistence.Account;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.graphql.test.tester.HttpGraphQlTester;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.web.servlet.client.MockMvcWebTestClient;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;


class SchemaPgApplicationTest extends AbstractIT {
    private HttpGraphQlTester graphQlTester;

    @BeforeEach
    void setUp(WebApplicationContext context) {
        WebTestClient client =
                MockMvcWebTestClient.bindToApplicationContext(context)
                        .configureClient()
                        .baseUrl("/graphql")
                        .build();
        graphQlTester = HttpGraphQlTester.create(client);
    }

    @Test
    @SuppressWarnings("unchecked")
    void schemaScan() {
        jdbcClient.insert("accounts")
                .param("balance", new BigDecimal("100.00"))
                .param("currency", "USD")
                .param("state", Account.State.ACTIVE)
                .execute();
        jdbcClient.insert("accounts")
                .param("balance", new BigDecimal("200.00"))
                .param("currency", "USD")
                .param("state", Account.State.ACTIVE)
                .execute();
        jdbcClient.insert("deposits")
                .param("amount", new BigDecimal("100.00"))
                .param("account_id", 1)
                .param("state", "REGISTERED")
                .param("registered_at", LocalDateTime.now())
                .param("registered_by", "admin")
                .execute();
        jdbcClient.insert("deposits")
                .param("amount", new BigDecimal("50.00"))
                .param("account_id", 2)
                .param("state", "CONFIRMED")
                .param("registered_at", LocalDateTime.now())
                .param("registered_by", "admin")
                .param("confirmed_at", LocalDateTime.now())
                .param("confirmed_by", "admin")
                .execute();
        jdbcClient.insert("deposits")
                .param("amount", new BigDecimal("150.00"))
                .param("account_id", 2)
                .param("state", "CONFIRMED")
                .param("registered_at", LocalDateTime.now())
                .param("registered_by", "admin")
                .param("confirmed_at", LocalDateTime.now())
                .param("confirmed_by", "admin")
                .execute();
        jdbcClient.insert("deposits")
                .param("amount", new BigDecimal("100.00"))
                .param("account_id", 2)
                .param("state", "REGISTERED")
                .param("registered_at", LocalDateTime.now())
                .param("registered_by", "admin")
                .execute();
        jdbcClient.insert("deposits")
                .param("amount", new BigDecimal("100.00"))
                .param("account_id", 2)
                .param("state", "REJECTED")
                .param("registered_at", LocalDateTime.now())
                .param("registered_by", "admin")
                .param("rejected_at", LocalDateTime.now())
                .param("rejected_by", "admin")
                .execute();
        ParameterizedTypeReference<Map<String, Object>> typeReference = new ParameterizedTypeReference<>() { };
        List<Map<String, Object>> deposits = graphQlTester.document("""
                        query {
                            deposits {
                                id
                                amount
                                state
                                account {
                                    id
                                    balance
                                }
                            }
                        }
                        """)
                .execute()
                .path("deposits")
                .entityList(typeReference)
                .get();
        assertThat(deposits)
                .hasSize(5)
                .extracting("amount", "state", "account.id", "account.balance")
                .containsExactly(
                        tuple(100.0, "REGISTERED", 1, 100.0),
                        tuple(50.0, "CONFIRMED", 2, 200.0),
                        tuple(150.0, "CONFIRMED", 2, 200.0),
                        tuple(100.0, "REGISTERED", 2, 200.0),
                        tuple(100.0, "REJECTED", 2, 200.0)
                );
        List<Map<String, Object>> registeredDeposits = graphQlTester.document("""
                        query {
                            deposits(where: { state: { _eq: "REGISTERED" } }) {
                                id
                                amount
                                state
                                account {
                                    id
                                    balance
                                }
                            }
                        }
                        """)
                .execute()
                .path("deposits")
                .entityList(typeReference)
                .get();
        List<Map<String, Object>> confirmedDepositsOver50 = graphQlTester.document("""
                        query {
                            deposits(where: { _and: [
                                { state: { _eq: "CONFIRMED" } },
                                { amount: { _gt: 50.0 } }
                            ] }) {
                                id
                                amount
                                state
                                account {
                                    id
                                    balance
                                }
                            }
                        }
                        """)
                .execute()
                .path("deposits")
                .entityList(typeReference)
                .get();
        List<Map<String, Object>> registeredDepositsForAccount2 = graphQlTester.document("""
                        query {
                            deposits(where: { _and: [
                            { state: { _eq: "REGISTERED" } },
                            { account_id: { _eq: 2 } }
                            ] }) {
                                id
                                amount
                                state
                                account {
                                    id
                                    balance
                                }
                            }
                        }
                        """)
                .execute()
                .path("deposits")
                .entityList(typeReference)
                .get();

        List<Map<String, Object>> accountsWithConfirmedDeposits = graphQlTester.document("""
                        query {
                            accounts {
                                id
                                balance
                                deposits(where: { state: { _eq: "CONFIRMED" } }) {
                                    id
                                    amount
                                    state
                                }
                            }
                        }
                        """)
                .execute()
                .path("accounts")
                .entityList(typeReference)
                .get();

        List<Map<String, Object>> accountsWithRejectedDeposits = graphQlTester.document("""
                        query {
                            accounts(where: { deposits: { state: { _eq: "REJECTED" } } }) {
                                id
                                balance
                                deposits(where: { state: { _eq: "REJECTED" } }) {
                                    id
                                    amount
                                    state
                                }
                            }
                        }
                        """)
                .execute()
                .path("accounts")
                .entityList(typeReference)
                .get();

        assertThat(confirmedDepositsOver50)
                .hasSize(1)
                .extracting("amount", "state", "account.id", "account.balance")
                .containsExactly(
                        tuple(150.0, "CONFIRMED", 2, 200.0)
                );
        assertThat(registeredDeposits)
                .hasSize(2)
                .extracting("amount", "state", "account.id", "account.balance")
                .containsExactly(
                        tuple(100.0, "REGISTERED", 1, 100.0),
                        tuple(100.0, "REGISTERED", 2, 200.0)
                );
        assertThat(registeredDepositsForAccount2)
                .hasSize(1)
                .extracting("amount", "state", "account.id", "account.balance")
                .containsExactly(
                        tuple(100.0, "REGISTERED", 2, 200.0)
                );
        assertThat(accountsWithConfirmedDeposits)
                .hasSize(2)
                .satisfiesExactly(
                        account -> {
                            assertThat(account)
                                    .extracting("id", "balance")
                                    .containsExactly(1, 100.0);
                            assertThat((Collection<Map<String, Object>>) account.get("deposits"))
                                    .as("Deposits for account with id 1")
                                    .isEmpty();
                        },
                        account -> {
                            assertThat(account)
                                    .extracting("id", "balance")
                                    .containsExactly(2, 200.0);
                            List<Map<String, Object>> accountDeposits = (List<Map<String, Object>>) account.get("deposits");
                            assertThat(accountDeposits)
                                    .as("Deposits for account with id 2")
                                    .hasSize(2)
                                    .extracting("amount", "state")
                                    .containsExactly(
                                            tuple(50.00, "CONFIRMED"),
                                            tuple(150.0, "CONFIRMED")
                                    );
                        });
        assertThat(accountsWithRejectedDeposits)
                .hasSize(1)
                .satisfiesExactly(
                        account -> {
                            assertThat(account)
                                    .extracting("id", "balance")
                                    .containsExactly(2, 200.0);
                            List<Map<String, Object>> accountDeposits = (List<Map<String, Object>>) account.get("deposits");
                            assertThat(accountDeposits)
                                    .as("Deposits for account with id 2")
                                    .hasSize(1)
                                    .extracting("amount", "state")
                                    .containsExactly(
                                            tuple(100.0, "REJECTED")
                                    );
                        });

    }

}
