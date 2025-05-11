package org.eljhoset.persistencepg.persistence;

import org.eljhoset.persistencepg.AbstractIT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;


class ConversionAwareUpdatableJdbcClientTest extends AbstractIT {
    @BeforeEach
    void setUp() {
        jdbcClient.sql("delete from deposits").update();
        jdbcClient.sql("delete from accounts").update();
    }
    @Test
    void polymorphicNested() {
        record Amount(BigDecimal value, Currency currency) { }
        interface DepositState { }
        interface ConfirmedDeposit extends DepositState { }
        record CompleteConfirmation(LocalDateTime confirmedAt, String confirmedBy) implements ConfirmedDeposit { }
        record IncompleteConfirmation(LocalDateTime confirmedAt, String confirmedBy) implements ConfirmedDeposit { }
        record Registered(LocalDateTime registeredAt, String registeredBy) implements DepositState { }
        record Deposit(Long accountId, Amount amount, DepositState state) { }

        Amount amount = new Amount(BigDecimal.TEN, Currency.getInstance("USD"));
        var keyHolder = new GeneratedKeyHolder();
        jdbcClient.insert("deposits")
                .param("account_id", 1L)
                .param(amount, it -> Map.of("amount", it.value(), "currency", it.currency()))
                .param("registered_at", LocalDateTime.now())
                .param("registered_by", "admin")
                .param("state", "REGISTERED")
                .execute(keyHolder, "id");

        Function<Number, Optional<Deposit>> depositSupplier = depositId -> jdbcClient.sql("""
                        select
                            id,
                            account_id,
                            state,
                            registered_at as state_registered_at,
                            registered_by as state_registered_by,
                            confirmed_at as state_confirmed_at,
                            confirmed_by as state_confirmed_by,
                            amount as amount_value,
                            currency as amount_currency,
                            is_confirmed
                        from deposits
                        where id = :id
                        """)
                .param("id", depositId)
                .<DepositState>columnDiscriminator("state", "state")
                .when("REGISTERED", Registered.class)
                .when("CONFIRMED", ConfirmedDeposit.class)
                .<ConfirmedDeposit>nestedColumnDiscriminator("state", "is_confirmed")
                .when(true, CompleteConfirmation.class)
                .when(false, IncompleteConfirmation.class)
                .query(Deposit.class)
                .optional();

        var id = keyHolder.getKey();
        Optional<Deposit> optionalDeposit = depositSupplier.apply(id);

        assertThat(optionalDeposit)
                .isPresent()
                .hasValueSatisfying(it -> {
                    System.out.println(it);
                    assertThat(it.accountId()).isEqualTo(1L);
                    assertThat(it.amount()).isEqualTo(amount);
                    assertThat(it.state()).isInstanceOfSatisfying(Registered.class, registered -> {
                        assertThat(registered.registeredBy()).isEqualTo("admin");
                        assertThat(registered.registeredAt()).isNotNull();
                    });
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
                    assertThat(it.state()).isInstanceOfSatisfying(IncompleteConfirmation.class, incompleteConfirmation -> {
                        assertThat(incompleteConfirmation.confirmedBy()).isEqualTo("admin");
                        assertThat(incompleteConfirmation.confirmedAt()).isNotNull();
                    });
                });

        jdbcClient.update("deposits")
                .param("is_confirmed", true)
                .where("id = :id", Map.of("id", id))
                .execute();

        optionalDeposit = depositSupplier.apply(id);
        assertThat(optionalDeposit)
                .isPresent()
                .hasValueSatisfying(it -> {
                    System.out.println(it);
                    assertThat(it.accountId()).isEqualTo(1L);
                    assertThat(it.amount()).isEqualTo(amount);
                    assertThat(it.state()).isInstanceOfSatisfying(CompleteConfirmation.class, completeConfirmation -> {
                        assertThat(completeConfirmation.confirmedBy()).isEqualTo("admin");
                        assertThat(completeConfirmation.confirmedAt()).isNotNull();
                    });
                });
    }

    @Test
    void polymorphicModelAndField() {
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
        interface RejectionReason { }
        record Other(String description) implements RejectionReason { }
        record Id(Long id) implements RejectionReason { }
        record RejectedDeposit(Long accountId, BigDecimal amount, RejectionReason rejectionReason,
                               LocalDateTime rejectedAt,
                               String rejectedBy) implements Deposit {
            @Override
            public DepositState state() {
                return DepositState.REJECTED;
            }
        }

        record CancelledDeposit(Long accountId, BigDecimal amount, Integer cancellationReasonId,
                                LocalDateTime cancelledAt,
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
                jdbcClient.sql("""
                                select
                                    *,
                                    rejection_reason_id as rejection_reason_id,
                                    rejection_reason_other as rejection_reason_description,
                                    rejection_reason_other is not null as is_other
                                from deposits where id = :id
                                """)
                        .param("id", depositId)
                        .<Deposit>columnDiscriminator("state")
                        .when("REGISTERED", RegisteredDeposit.class)
                        .when("APPROVED", ApprovedDeposit.class)
                        .when("REJECTED", RejectedDeposit.class)
                        .when("CANCELLED", CancelledDeposit.class)
                        .when("CONFIRMED", ConfirmedDeposit.class)
                        .columnDiscriminator("rejectionReason", "is_other")
                        .when(true, Other.class)
                        .when(false, Id.class)
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
                    assertThat(it).isInstanceOfSatisfying(RejectedDeposit.class, rejectedDeposit ->
                            assertThat(rejectedDeposit.rejectionReason()).isInstanceOfSatisfying(Other.class, other ->
                                    assertThat(other.description()).isEqualTo("Insufficient funds")));
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
                    assertThat(it.amount()).isEqualTo(BigDecimal.TEN);
                    assertThat(it.state()).isEqualTo(DepositState.REJECTED);
                    assertThat(it).isInstanceOfSatisfying(RejectedDeposit.class, rejectedDeposit ->
                            assertThat(rejectedDeposit.rejectionReason()).isInstanceOfSatisfying(Id.class, rejectionId ->
                                    assertThat(rejectionId.id()).isEqualTo(1)));
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
    void polymorphicFieldNested() {
        interface DepositState { }
        record Amount(BigDecimal value, Currency currency) { }
        record Deposit(Long accountId, Amount amount, DepositState state) { }
        record Registered(LocalDateTime registeredAt, String registeredBy) implements DepositState { }
        record Approved(LocalDateTime approvedAt, String approvedBy) implements DepositState { }
        interface RejectionReason { }
        record Other(String description) implements RejectionReason { }
        record Id(Long id) implements RejectionReason { }
        record RejectedDeposit(RejectionReason rejectionReason, LocalDateTime rejectedAt,
                               String rejectedBy) implements DepositState { }
        record CancelledDeposit(Integer cancellationReasonId, LocalDateTime cancelledAt,
                                String cancelledBy) implements DepositState { }
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
                                    registered_at as state_registered_at,
                                    registered_by as state_registered_by,
                                    approved_at as state_approved_at,
                                    approved_by as state_approved_by,
                                    rejected_at as state_rejected_at,
                                    rejected_by as state_rejected_by,
                                    rejection_reason_id as state_rejection_reason_id,
                                    rejection_reason_other as state_rejection_reason_description,
                                    rejection_reason_other is not null as is_other,
                                    cancelled_at as state_cancelled_at,
                                    cancelled_by as state_cancelled_by,
                                    cancellation_reason_id as state_cancellation_reason_id,
                                    confirmed_at as state_confirmed_at,
                                    confirmed_by as state_confirmed_by
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
                        .<RejectionReason>columnDiscriminator("rejectionReason", "is_other")
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
                                    assertThat(other.description()).isEqualTo("Insufficient funds")));
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
                                    assertThat(rejectionId.id()).isEqualTo(1)));
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
        record RejectedDeposit(Integer rejectionReasonId, LocalDateTime rejectedAt,
                               String rejectedBy) implements DepositState { }
        record CancelledDeposit(Integer cancellationReasonId, LocalDateTime cancelledAt,
                                String cancelledBy) implements DepositState { }
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

        record CancelledDeposit(Long accountId, BigDecimal amount, Integer cancellationReasonId,
                                LocalDateTime cancelledAt,
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
    void pagination() {
        jdbcClient.sql("delete from accounts").update();
        record Account(Long id, BigDecimal balance, String currency, String state) { }
        jdbcClient.insert("accounts")
                .param("balance", BigDecimal.TEN)
                .param("currency", "CAD")
                .param("state", "ACTIVE")
                .execute();
        jdbcClient.insert("accounts")
                .param("balance", BigDecimal.TEN)
                .param("currency", "USD")
                .param("state", "ACTIVE")
                .execute();
        jdbcClient.insert("accounts")
                .param("balance", BigDecimal.TEN)
                .param("currency", "USD")
                .param("state", "ACTIVE")
                .execute();

        jdbcClient.insert("accounts")
                .param("balance", BigDecimal.TEN)
                .param("currency", "USD")
                .param("state", "DISABLED")
                .execute();

        String sql = "select * from accounts";
        PageRequest pageRequest = PageRequest.of(0, 2);
        var page = jdbcClient.sql(sql)
                .query(Account.class, pageRequest);

        assertThat(page).hasSize(2);
        assertThat(page.getTotalElements()).isEqualTo(4);
        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(page.getNumber()).isZero();
        assertThat(page.getSize()).isEqualTo(2);

        page = jdbcClient.sql("select * from accounts where state = :state")
                .param("state", "ACTIVE")
                .query(Account.class, pageRequest);

        assertThat(page).hasSize(2);
        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(page.getNumber()).isZero();
        assertThat(page.getSize()).isEqualTo(2);

        pageRequest = PageRequest.of(1, 2, Sort.by(Sort.Order.desc("currency")));
        jdbcClient.sql("select * from accounts where state = :state")
                .param("state", "ACTIVE")
                .query(Account.class, pageRequest);

        assertThat(page).hasSize(2);
        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(page.getNumber()).isZero();
        assertThat(page.getSize()).isEqualTo(2);
        assertThat(page.getContent()).first().satisfies(it -> assertThat(it.currency()).isEqualTo("CAD"));
    }

    @Test
    void batchUpdate() {
        jdbcClient.sql("delete from accounts").update();
        record Account(Long id, BigDecimal balance, String currency, String state) { }
        var canadian = jdbcClient.insert("accounts")
                .param("balance", BigDecimal.TEN)
                .param("currency", "CAD")
                .param("state", "ACTIVE")
                .execute("id").map(KeyHolder::getKey);

        var usDollars = jdbcClient.insert("accounts")
                .param("balance", BigDecimal.TEN)
                .param("currency", "USD")
                .param("state", "ACTIVE")
                .execute("id").map(KeyHolder::getKey);

        var usDollarsTwo = jdbcClient.insert("accounts")
                .param("balance", BigDecimal.TEN)
                .param("currency", "USD")
                .param("state", "ACTIVE")
                .execute("id").map(KeyHolder::getKey);

        jdbcClient.batchUpdate("accounts")
                .where("id = :id", "id")
                .param("balance", BigDecimal.ZERO)
                .param("state", "ACTIVE")
                .param("id", canadian)
                .also()
                .param("balance", BigDecimal.TEN)
                .param("state", "DISABLED")
                .param("id", usDollars)
                .execute();

        var canadianAccount = jdbcClient.sql("select * from accounts where id = :id")
                .param("id", canadian)
                .query(Account.class)
                .optional();

        var usDollarsAccount = jdbcClient.sql("select * from accounts where id = :id")
                .param("id", usDollars)
                .query(Account.class)
                .optional();

        assertThat(canadianAccount)
                .isPresent()
                .hasValueSatisfying(it -> assertThat(it.balance()).isZero());

        assertThat(usDollarsAccount)
                .isPresent()
                .hasValueSatisfying(it -> assertThat(it.balance()).isEqualTo(BigDecimal.TEN));

    }

    @Test
    void masterDetail() {
        jdbcClient.insert("accounts")
                .param("balance", new BigDecimal("200.00"))
                .param("currency", "USD")
                .param("state", State.ACTIVE)
                .execute();
        jdbcClient.insert("deposits")
                .param("amount", new BigDecimal("100.00"))
                .param("account_id", 1)
                .param("state", "REGISTERED")
                .param("registered_at", LocalDateTime.now())
                .param("registered_by", "admin")
                .execute();
        jdbcClient.insert("deposits")
                .param("amount", new BigDecimal("200.00"))
                .param("account_id", 1)
                .param("state", "REGISTERED")
                .param("registered_at", LocalDateTime.now())
                .param("registered_by", "admin")
                .execute();

        record Deposit(BigDecimal amount) { }
        record Account(State state, Collection<Deposit> deposits) { }

        List<Account> list = jdbcClient.sql("""
                        select a.id, a.state, d.amount as deposits_amount
                        from accounts a
                        join deposits d on d.account_id = a.id
                        """)
                .group("deposits").by("id")
                .query(Account.class)
                .list();

        assertThat(list)
                .hasSize(1)
                .first()
                .satisfies(it -> {
                    assertThat(it.state()).isEqualTo(State.ACTIVE);
                    assertThat(it.deposits()).hasSize(2);
                    assertThat(it.deposits())
                            .extracting(Deposit::amount)
                            .containsExactlyInAnyOrder(
                                    new BigDecimal("100.00"),
                                    new BigDecimal("200.00")
                            );
                });
    }
}
