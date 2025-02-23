package org.eljhoset.persistencepg.persistence;

import org.eljhoset.persistencepg.AbstractIT;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Currency;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class ConversionAwareUpdatableJdbcClientTest extends AbstractIT {
    @Test
    void polymorphicFieldNested() {
        interface DepositState { }
        record Amount(BigDecimal value, Currency currency) { }
        record Deposit(Long accountId, Amount amount, DepositState state) { }
        record Registered(LocalDateTime registeredAt, String registeredBy) implements DepositState { }
        record Approved(LocalDateTime approvedAt, String approvedBy) implements DepositState { }
        interface RejectionReason { }
        record Other(String rejectionReasonDescription) implements RejectionReason{}
        record Id(Long rejectionReasonId) implements RejectionReason{}
        record RejectedDeposit(RejectionReason rejectionReason, LocalDateTime rejectedAt, String rejectedBy) implements DepositState { }
        record CancelledDeposit(Integer cancellationReasonId, LocalDateTime cancelledAt, String cancelledBy) implements DepositState { }
        record ConfirmedDeposit(LocalDateTime confirmedAt, String confirmedBy) implements DepositState { }

        Amount amount = new Amount(BigDecimal.TEN, Currency.getInstance("USD"));

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcClient.insert("deposits")
                .param("account_id", 1L)
                .param(amount, it -> Map.of("amount", it.value(), "currency", it.currency()))
                .param("registered_at", LocalDateTime.now())
                .param("registered_by", "admin")
                .param("state", "REGISTERED")
                .execute(keyHolder, "id");
        var id = keyHolder.getKey();

        Function<Number, Optional<Deposit>> depositSupplier = depositId ->
                jdbcClient.sql("""
                                select
                                    id,
                                    account_id,
                                    state,
                                    amount as amount_value,
                                    currency as amount_currency,
                                    registered_at,
                                    registered_by,
                                    approved_at,
                                    approved_by,
                                    rejected_at,
                                    rejected_by,
                                    rejection_reason_id,
                                    rejection_reason_other as rejection_reason_description,
                                    rejection_reason_other is not null as is_other,
                                    cancelled_at,
                                    cancelled_by,
                                    cancellation_reason_id,
                                    confirmed_at,
                                    confirmed_by
                                from
                                    deposits
                                where
                                    id = :id
                                """)
                        .param("id", depositId)
                        .<DepositState>columnDiscriminator("state", "state")
                        .when("REGISTERED", Registered.class)
                        .when("APPROVED", Approved.class)
                        .when("REJECTED", RejectedDeposit.class)
                        .when("CANCELLED", CancelledDeposit.class)
                        .when("CONFIRMED", ConfirmedDeposit.class)
                        .<RejectionReason>columnDiscriminator("rejectionReason","is_other")
                        .when(true, Other.class)
                        .when(false, Id.class)
                        .query(Deposit.class)
                        .optional();

        Optional<Deposit> optionalDeposit = depositSupplier.apply(id);

        assertThat(optionalDeposit)
                .isPresent()
                .hasValueSatisfying(it -> {
                    System.out.println(it);
                    assertThat(it.accountId()).isEqualTo(1L);
                    assertThat(it.amount()).isEqualTo(amount);
                    assertThat(it.state()).isInstanceOf(Registered.class);
                });

        jdbcClient.update("deposits")
                .param("state", "APPROVED")
                .param("approved_at", LocalDateTime.now())
                .param("approved_by", "admin")
                .where("id = :id", Map.of("id", id))
                .execute();

        optionalDeposit = depositSupplier.apply(id);

        assertThat(optionalDeposit)
                .isPresent()
                .hasValueSatisfying(it -> {
                    System.out.println(it);
                    assertThat(it.accountId()).isEqualTo(1L);
                    assertThat(it.amount()).isEqualTo(amount);
                    assertThat(it.state()).isInstanceOf(Approved.class);
                });

        jdbcClient.update("deposits")
                .param("state", "REJECTED")
                .param("rejection_reason_id", 6)
                .param("rejection_reason_other", "Insufficient funds")
                .param("rejected_at", LocalDateTime.now())
                .param("rejected_by", "admin")
                .where("id = :id", Map.of("id", id))
                .execute();

        optionalDeposit = depositSupplier.apply(id);

        assertThat(optionalDeposit)
                .isPresent()
                .hasValueSatisfying(it -> {
                    System.out.println(it);
                    assertThat(it.accountId()).isEqualTo(1L);
                    assertThat(it.amount()).isEqualTo(amount);
                    assertThat(it.state()).isInstanceOfSatisfying(RejectedDeposit.class, rejectedDeposit ->
                            assertThat(rejectedDeposit.rejectionReason()).isInstanceOfSatisfying(Other.class, other ->
                                    assertThat(other.rejectionReasonDescription()).isEqualTo("Insufficient funds")));
                });

        jdbcClient.update("deposits")
                .param("state", "REJECTED")
                .param("rejection_reason_id", 1)
                .setNull("rejection_reason_other")
                .param("rejected_at", LocalDateTime.now())
                .param("rejected_by", "admin")
                .where("id = :id", Map.of("id", id))
                .execute();

        optionalDeposit = depositSupplier.apply(id);

        assertThat(optionalDeposit)
                .isPresent()
                .hasValueSatisfying(it -> {
                    System.out.println(it);
                    assertThat(it.accountId()).isEqualTo(1L);
                    assertThat(it.amount()).isEqualTo(amount);
                    assertThat(it.state()).isInstanceOfSatisfying(RejectedDeposit.class, rejectedDeposit ->
                            assertThat(rejectedDeposit.rejectionReason()).isInstanceOfSatisfying(Id.class, rejectionId ->
                                    assertThat(rejectionId.rejectionReasonId()).isEqualTo(1)));
                });

        jdbcClient.update("deposits")
                .param("state", "CANCELLED")
                .param("cancellation_reason_id", 6)
                .param("cancellation_reason_other", "User request")
                .param("cancelled_at", LocalDateTime.now())
                .param("cancelled_by", "admin")
                .where("id = :id", Map.of("id", id))
                .execute();

        optionalDeposit = depositSupplier.apply(id);

        assertThat(optionalDeposit)
                .isPresent()
                .hasValueSatisfying(it -> {
                    System.out.println(it);
                    assertThat(it.accountId()).isEqualTo(1L);
                    assertThat(it.amount()).isEqualTo(amount);
                    assertThat(it.state()).isInstanceOf(CancelledDeposit.class);
                });

        jdbcClient.update("deposits")
                .param("state", "CONFIRMED")
                .param("confirmed_at", LocalDateTime.now())
                .param("confirmed_by", "admin")
                .where("id = :id", Map.of("id", id))
                .execute();

        optionalDeposit = depositSupplier.apply(id);

        assertThat(optionalDeposit)
                .isPresent()
                .hasValueSatisfying(it -> {
                    System.out.println(it);
                    assertThat(it.accountId()).isEqualTo(1L);
                    assertThat(it.amount()).isEqualTo(amount);
                    assertThat(it.state()).isInstanceOf(ConfirmedDeposit.class);
                });

    }

    @Test
    void polymorphicField() {
        interface DepositState { }
        record Amount(BigDecimal value, Currency currency) { }
        record Deposit(Long accountId, Amount amount, DepositState state) { }
        record Registered(LocalDateTime registeredAt, String registeredBy) implements DepositState { }
        record Approved(LocalDateTime approvedAt, String approvedBy) implements DepositState { }
        record RejectedDeposit(Integer rejectionReasonId, LocalDateTime rejectedAt, String rejectedBy) implements DepositState { }
        record CancelledDeposit(Integer cancellationReasonId, LocalDateTime cancelledAt, String cancelledBy) implements DepositState { }
        record ConfirmedDeposit(LocalDateTime confirmedAt, String confirmedBy) implements DepositState { }

        Amount amount = new Amount(BigDecimal.TEN, Currency.getInstance("USD"));

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcClient.insert("deposits")
                .param("account_id", 1L)
                .param(amount, it -> Map.of("amount", it.value(), "currency", it.currency()))
                .param("registered_at", LocalDateTime.now())
                .param("registered_by", "admin")
                .param("state", "REGISTERED")
                .execute(keyHolder, "id");
        var id = keyHolder.getKey();

        Function<Number, Optional<Deposit>> depositSupplier = depositId ->
                jdbcClient.sql("""
                                select
                                    id,
                                    account_id,
                                    state,
                                    amount as amount_value,
                                    currency as amount_currency,
                                    registered_at,
                                    registered_by,
                                    approved_at,
                                    approved_by,
                                    rejected_at,
                                    rejected_by,
                                    rejection_reason_id,
                                    cancelled_at,
                                    cancelled_by,
                                    cancellation_reason_id,
                                    confirmed_at,
                                    confirmed_by
                                from
                                    deposits
                                where
                                    id = :id
                                """)
                        .param("id", depositId)
                        .<DepositState>columnDiscriminator("state", "state")
                            .when("REGISTERED", Registered.class)
                            .when("APPROVED", Approved.class)
                            .when("REJECTED", RejectedDeposit.class)
                            .when("CANCELLED", CancelledDeposit.class)
                            .when("CONFIRMED", ConfirmedDeposit.class)
                        .query(Deposit.class)
                        .optional();

        Optional<Deposit> optionalDeposit = depositSupplier.apply(id);

        assertThat(optionalDeposit)
                .isPresent()
                .hasValueSatisfying(it -> {
                    System.out.println(it);
                    assertThat(it.accountId()).isEqualTo(1L);
                    assertThat(it.amount()).isEqualTo(amount);
                    assertThat(it.state()).isInstanceOf(Registered.class);
                });

        jdbcClient.update("deposits")
                .param("state", "APPROVED")
                .param("approved_at", LocalDateTime.now())
                .param("approved_by", "admin")
                .where("id = :id", Map.of("id", id))
                .execute();

        optionalDeposit = depositSupplier.apply(id);

        assertThat(optionalDeposit)
                .isPresent()
                .hasValueSatisfying(it -> {
                    System.out.println(it);
                    assertThat(it.accountId()).isEqualTo(1L);
                    assertThat(it.amount()).isEqualTo(amount);
                    assertThat(it.state()).isInstanceOf(Approved.class);
                });

        jdbcClient.update("deposits")
                .param("state", "REJECTED")
                .param("rejection_reason_id", 6)
                .param("rejection_reason_other", "Insufficient funds")
                .param("rejected_at", LocalDateTime.now())
                .param("rejected_by", "admin")
                .where("id = :id", Map.of("id", id))
                .execute();

        optionalDeposit = depositSupplier.apply(id);

        assertThat(optionalDeposit)
                .isPresent()
                .hasValueSatisfying(it -> {
                    System.out.println(it);
                    assertThat(it.accountId()).isEqualTo(1L);
                    assertThat(it.amount()).isEqualTo(amount);
                    assertThat(it.state()).isInstanceOf(RejectedDeposit.class);
                });

        jdbcClient.update("deposits")
                .param("state", "CANCELLED")
                .param("cancellation_reason_id", 6)
                .param("cancellation_reason_other", "User request")
                .param("cancelled_at", LocalDateTime.now())
                .param("cancelled_by", "admin")
                .where("id = :id", Map.of("id", id))
                .execute();

        optionalDeposit = depositSupplier.apply(id);

        assertThat(optionalDeposit)
                .isPresent()
                .hasValueSatisfying(it -> {
                    System.out.println(it);
                    assertThat(it.accountId()).isEqualTo(1L);
                    assertThat(it.amount()).isEqualTo(amount);
                    assertThat(it.state()).isInstanceOf(CancelledDeposit.class);
                });

        jdbcClient.update("deposits")
                .param("state", "CONFIRMED")
                .param("confirmed_at", LocalDateTime.now())
                .param("confirmed_by", "admin")
                .where("id = :id", Map.of("id", id))
                .execute();

        optionalDeposit = depositSupplier.apply(id);

        assertThat(optionalDeposit)
                .isPresent()
                .hasValueSatisfying(it -> {
                    System.out.println(it);
                    assertThat(it.accountId()).isEqualTo(1L);
                    assertThat(it.amount()).isEqualTo(amount);
                    assertThat(it.state()).isInstanceOf(ConfirmedDeposit.class);
                });

    }

    @Test
    void polymorphicModel() {
        enum DepositState {
            REGISTERED, APPROVED, REJECTED, CANCELLED, CONFIRMED
        }
        interface Deposit {
            Long accountId();
            DepositState state();
            BigDecimal amount();
        }
        record RegisteredDeposit(Long accountId, BigDecimal amount, LocalDateTime registeredAt,
                                 String registeredBy) implements Deposit {
            @Override
            public DepositState state() {
                return DepositState.REGISTERED;
            }
        }
        record ApprovedDeposit(Long accountId, BigDecimal amount, LocalDateTime approvedAt,
                               String approvedBy) implements Deposit {
            @Override
            public DepositState state() {
                return DepositState.APPROVED;
            }
        }
        record RejectedDeposit(Long accountId, BigDecimal amount, Integer rejectionReasonId, LocalDateTime rejectedAt,
                               String rejectedBy) implements Deposit {
            @Override
            public DepositState state() {
                return DepositState.REJECTED;
            }
        }

        record CancelledDeposit(Long accountId, BigDecimal amount, Integer cancellationReasonId, LocalDateTime cancelledAt,
                                String cancelledBy) implements Deposit {
            @Override
            public DepositState state() {
                return DepositState.CANCELLED;
            }
        }

        record ConfirmedDeposit(Long accountId, BigDecimal amount, LocalDateTime confirmedAt,
                                String confirmedBy) implements Deposit {
            @Override
            public DepositState state() {
                return DepositState.CONFIRMED;
            }
        }
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcClient.sql("""
                        insert into deposits (account_id, amount, state, registered_at, registered_by)
                        values (:accountId, :amount, 'REGISTERED', :registeredAt, :registeredBy)
                        """)
                .param("accountId", 1L)
                .param("amount", BigDecimal.TEN)
                .param("registeredAt", LocalDateTime.now())
                .param("registeredBy", "admin")
                .update(keyHolder, "id");
        var id = keyHolder.getKey();

        Function<Number, Optional<Deposit>> depositSupplier = depositId ->
                jdbcClient.sql("select * from deposits where id = :id")
                        .param("id", depositId)
                        .<Deposit>columnDiscriminator("state")
                        .when("REGISTERED", RegisteredDeposit.class)
                        .when("APPROVED", ApprovedDeposit.class)
                        .when("REJECTED", RejectedDeposit.class)
                        .when("CANCELLED", CancelledDeposit.class)
                        .when("CONFIRMED", ConfirmedDeposit.class)
                        .query()
                        .optional();

        Optional<Deposit> optionalDeposit = depositSupplier.apply(id);

        assertThat(optionalDeposit)
                .isPresent()
                .hasValueSatisfying(it -> {
                    System.out.println(it);
                    assertThat(it.accountId()).isEqualTo(1L);
                    assertThat(it.amount()).isEqualTo(BigDecimal.TEN);
                    assertThat(it.state()).isEqualTo(DepositState.REGISTERED);
                });

        jdbcClient.update("deposits")
                .param("state", "APPROVED")
                .param("approved_at", LocalDateTime.now())
                .param("approved_by", "admin")
                .where("id = :id", Map.of("id", id))
                .execute();

        optionalDeposit = depositSupplier.apply(id);

        assertThat(optionalDeposit)
                .isPresent()
                .hasValueSatisfying(it -> {
                    System.out.println(it);
                    assertThat(it.accountId()).isEqualTo(1L);
                    assertThat(it.amount()).isEqualTo(BigDecimal.TEN);
                    assertThat(it.state()).isEqualTo(DepositState.APPROVED);
                });

        jdbcClient.update("deposits")
                .param("state", "REJECTED")
                .param("rejection_reason_id", 6)
                .param("rejection_reason_other", "Insufficient funds")
                .param("rejected_at", LocalDateTime.now())
                .param("rejected_by", "admin")
                .where("id = :id", Map.of("id", id))
                .execute();

        optionalDeposit = depositSupplier.apply(id);

        assertThat(optionalDeposit)
                .isPresent()
                .hasValueSatisfying(it -> {
                    System.out.println(it);
                    assertThat(it.accountId()).isEqualTo(1L);
                    assertThat(it.amount()).isEqualTo(BigDecimal.TEN);
                    assertThat(it.state()).isEqualTo(DepositState.REJECTED);
                });

        jdbcClient.update("deposits")
                .param("state", "CANCELLED")
                .param("cancellation_reason_id", 6)
                .param("cancellation_reason_other", "User request")
                .param("cancelled_at", LocalDateTime.now())
                .param("cancelled_by", "admin")
                .where("id = :id", Map.of("id", id))
                .execute();

        optionalDeposit = depositSupplier.apply(id);

        assertThat(optionalDeposit)
                .isPresent()
                .hasValueSatisfying(it -> {
                    System.out.println(it);
                    assertThat(it.accountId()).isEqualTo(1L);
                    assertThat(it.amount()).isEqualTo(BigDecimal.TEN);
                    assertThat(it.state()).isEqualTo(DepositState.CANCELLED);
                });

        jdbcClient.update("deposits")
                .param("state", "CONFIRMED")
                .param("confirmed_at", LocalDateTime.now())
                .param("confirmed_by", "admin")
                .where("id = :id", Map.of("id", id))
                .execute();

        optionalDeposit = depositSupplier.apply(id);

        assertThat(optionalDeposit)
                .isPresent()
                .hasValueSatisfying(it -> {
                    System.out.println(it);
                    assertThat(it.accountId()).isEqualTo(1L);
                    assertThat(it.amount()).isEqualTo(BigDecimal.TEN);
                    assertThat(it.state()).isEqualTo(DepositState.CONFIRMED);
                });

    }

    @Test
    void jdbcPartialUpdate() {
        Account.Balance balance = new Account.Balance(BigDecimal.valueOf(1000), Currency.getInstance("USD"));
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcClient.insert("accounts")
                .compositeParam(balance)
                .param("state", Account.State.ACTIVE)
                .execute(keyHolder, "id");
        var id = keyHolder.getKey();
        Optional<Account> optionalAccount = jdbcClient.sql("""
                        SELECT
                            id, balance as balance_value, currency as balance_currency, state
                        FROM
                            accounts
                        WHERE id = :id
                        """)
                .param("id", id)
                .query(Account.class)
                .optional();
        assertThat(optionalAccount)
                .isPresent()
                .hasValueSatisfying(it -> {
                    assertThat(it.getBalance()).isEqualTo(balance);
                    assertThat(it.getState()).isEqualTo(Account.State.ACTIVE);
                });

        jdbcClient.update("accounts")
                .param("state", Account.State.CLOSED)
                .where("id = :id", Map.of("id", id))
                .execute();

        optionalAccount = jdbcClient.sql("""
                        SELECT
                            id, balance as balance_value, currency as balance_currency, state
                        FROM
                            accounts
                        WHERE id = :id
                        """)
                .param("id", id)
                .query(Account.class)
                .optional();

        assertThat(optionalAccount)
                .isPresent()
                .hasValueSatisfying(it -> {
                    assertThat(it.getBalance()).isEqualTo(balance);
                    assertThat(it.getState()).isEqualTo(Account.State.CLOSED);
                });

    }
    @Disabled
    @Test
    void jpaUpdate() {
        Account.Balance balance = new Account.Balance(BigDecimal.valueOf(1000), Currency.getInstance("USD"));

        Account account = new Account();
        account.setBalance(balance);
        account.setState(Account.State.ACTIVE);
        account = accountRepository.save(account);

        assertThat(accountRepository.findById(account.getId()))
                .isPresent()
                .hasValueSatisfying(it -> {
                    assertThat(it.getBalance()).isEqualTo(balance);
                    assertThat(it.getState()).isEqualTo(Account.State.ACTIVE);
                });

        Account accountWithStateUpdated = new Account();
        accountWithStateUpdated.setId(account.getId());
        accountWithStateUpdated.setState(Account.State.CLOSED);
        accountRepository.save(accountWithStateUpdated);

        assertThat(accountRepository.findById(account.getId()))
                .isPresent()
                .hasValueSatisfying(it -> {
                    assertThat(it.getBalance()).isEqualTo(balance);
                    assertThat(it.getState()).isEqualTo(Account.State.CLOSED);
                });
    }
}
