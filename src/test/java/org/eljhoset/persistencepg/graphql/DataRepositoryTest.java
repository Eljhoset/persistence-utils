package org.eljhoset.persistencepg.graphql;

import org.eljhoset.persistencepg.AbstractIT;
import org.eljhoset.persistencepg.graphql.filter.SqlFragment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.AssertionsForClassTypes.tuple;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

class DataRepositoryTest extends AbstractIT {
    @BeforeEach
    void setup() {
        jdbcClient.sql("delete from accounts").update();
    }
    @Test
    void genericTableData(){
        jdbcClient.insert("accounts")
                .param("balance", 100.00)
                .param("currency", "USD")
                .param("state", "REGISTERED")
                .execute();

        List<Map<String, Object>> accounts = dataRepository.genericTableData("accounts", SqlFragment.empty());
        assertThat(accounts)
                .hasSize(1)
                .extracting("balance", "currency", "state")
                .containsExactly(tuple(new BigDecimal("100"), "USD", "REGISTERED"));
    }

    @Test
    void genericTableWithFilter(){
        jdbcClient.insert("accounts")
                .param("balance", 100.00)
                .param("currency", "USD")
                .param("state", "REGISTERED")
                .execute();
        jdbcClient.insert("accounts")
                .param("balance", 200.00)
                .param("currency", "USD")
                .param("state", "ACTIVE")
                .execute();
        List<Map<String, Object>> accounts = dataRepository.genericTableData("accounts", SqlFragment.empty());
        assertThat(accounts)
                .hasSize(2)
                .extracting("balance", "currency", "state")
                .containsExactly(
                        tuple(new BigDecimal("100"), "USD", "REGISTERED"),
                        tuple(new BigDecimal("200"), "USD", "ACTIVE")
                );
        SqlFragment sqlFragment = new SqlFragment("state = :state", Map.of("state", "REGISTERED"), List.of());
        List<Map<String, Object>> accountsFiltered = dataRepository.genericTableData("accounts", sqlFragment);
        assertThat(accountsFiltered)
                .hasSize(1)
                .extracting("balance", "currency", "state")
                .containsExactly(tuple(new BigDecimal("100"), "USD", "REGISTERED"));
    }

    @Test
    void genericTableWithFilterAndJoin(){
        jdbcClient.insert("accounts")
                .param("balance", 100.00)
                .param("currency", "USD")
                .param("state", "REGISTERED")
                .execute();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcClient.insert("accounts")
                .param("balance", 200.00)
                .param("currency", "USD")
                .param("state", "ACTIVE")
                .execute(keyHolder, "id");
        jdbcClient.insert("deposits")
                .param("amount", 100.00)
                .param("account_id", keyHolder.getKey())
                .param("state", "CONFIRMED")
                .param("registered_at", LocalDateTime.now())
                .param("registered_by", "admin")
                .execute();
        List<Map<String, Object>> accounts = dataRepository.genericTableData("accounts", SqlFragment.empty());
        assertThat(accounts)
                .hasSize(2)
                .extracting("balance", "currency", "state")
                .containsExactly(
                        tuple(new BigDecimal("100"), "USD", "REGISTERED"),
                        tuple(new BigDecimal("200"), "USD", "ACTIVE")
                );
        SqlFragment sqlFragment = new SqlFragment("accounts.state = :state_1 AND deposits.state = :state_2",
                Map.of("state_1", "ACTIVE", "state_2", "CONFIRMED"),
                List.of("JOIN deposits deposits ON accounts.id = deposits.account_id"));
        List<Map<String, Object>> accountsFiltered = dataRepository.genericTableData("accounts", sqlFragment);
        assertThat(accountsFiltered)
                .hasSize(1)
                .extracting("id","balance", "currency", "state")
                .containsExactly(tuple(keyHolder.getKey(), new BigDecimal("200"), "USD", "ACTIVE"));
    }

    @Test
    void genericTableDataByColumn(){
        jdbcClient.insert("accounts")
                .param("balance", 100.00)
                .param("currency", "USD")
                .param("state", "REGISTERED")
                .execute();
        jdbcClient.insert("accounts")
                .param("balance", 200.00)
                .param("currency", "USD")
                .param("state", "ACTIVE")
                .execute();
        List<Map<String, Object>> accounts = dataRepository.genericTableData("accounts", SqlFragment.empty());
        assertThat(accounts)
                .hasSize(2)
                .extracting("balance", "currency", "state")
                .containsExactly(
                        tuple(new BigDecimal("100"), "USD", "REGISTERED"),
                        tuple(new BigDecimal("200"), "USD", "ACTIVE")
                );
        SqlFragment sqlFragment = SqlFragment.empty();
        Collection<Map<String, Object>> accountsFiltered = dataRepository.genericTableDataByColumn("accounts", "state", List.of("REGISTERED"), sqlFragment);
        assertThat(accountsFiltered)
                .hasSize(1)
                .extracting("balance", "currency", "state")
                .containsExactly(tuple(new BigDecimal("100"), "USD", "REGISTERED"));
    }

    @Test
    void genericTableDataByColumnWithFilter(){
        jdbcClient.insert("accounts")
                .param("balance", 100.00)
                .param("currency", "USD")
                .param("state", "REGISTERED")
                .execute();
        jdbcClient.insert("accounts")
                .param("balance", 200.00)
                .param("currency", "USD")
                .param("state", "ACTIVE")
                .execute();
        List<Map<String, Object>> accounts = dataRepository.genericTableData("accounts", SqlFragment.empty());
        assertThat(accounts)
                .hasSize(2)
                .extracting("balance", "currency", "state")
                .containsExactly(
                        tuple(new BigDecimal("100"), "USD", "REGISTERED"),
                        tuple(new BigDecimal("200"), "USD", "ACTIVE")
                );
        SqlFragment sqlFragment = new SqlFragment("state = :state", Map.of("state", "REGISTERED"), List.of());
        Collection<Map<String, Object>> accountsFiltered = dataRepository.genericTableDataByColumn("accounts", "state", List.of("REGISTERED"), sqlFragment);
        assertThat(accountsFiltered)
                .hasSize(1)
                .extracting("balance", "currency", "state")
                .containsExactly(tuple(new BigDecimal("100"), "USD", "REGISTERED"));
    }

    @Test
    void genericTableDataByColumnWithFilterAndJoin(){
        jdbcClient.insert("accounts")
                .param("balance", 100.00)
                .param("currency", "USD")
                .param("state", "REGISTERED")
                .execute();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcClient.insert("accounts")
                .param("balance", 200.00)
                .param("currency", "USD")
                .param("state", "ACTIVE")
                .execute(keyHolder, "id");
        jdbcClient.insert("deposits")
                .param("amount", 100.00)
                .param("account_id", keyHolder.getKey())
                .param("state", "CONFIRMED")
                .param("registered_at", LocalDateTime.now())
                .param("registered_by", "admin")
                .execute();
        List<Map<String, Object>> accounts = dataRepository.genericTableData("accounts", SqlFragment.empty());
        assertThat(accounts)
                .hasSize(2)
                .extracting("balance", "currency", "state")
                .containsExactly(
                        tuple(new BigDecimal("100"), "USD", "REGISTERED"),
                        tuple(new BigDecimal("200"), "USD", "ACTIVE")
                );
        SqlFragment sqlFragment = new SqlFragment("accounts.state = :state_1 AND deposits.state = :state_2",
                Map.of("state_1", "ACTIVE", "state_2", "CONFIRMED"),
                List.of("JOIN deposits deposits ON accounts.id = deposits.account_id"));
        Collection<Map<String, Object>> accountsFiltered = dataRepository.genericTableDataByColumn("accounts", "state", List.of("ACTIVE"), sqlFragment);
        assertThat(accountsFiltered)
                .hasSize(1)
                .extracting("id","balance", "currency", "state")
                .containsExactly(tuple(keyHolder.getKey(), new BigDecimal("200"), "USD", "ACTIVE"));
    }
}
