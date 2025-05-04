package org.eljhoset.persistencepg.persistence;

import org.eljhoset.persistencepg.AbstractIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;


class ConversionAwareUpdatableJdbcClientTest extends AbstractIT {
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

        record Deposit(BigDecimal amount) {}
        record Account(State state, Collection<Deposit> deposits) {}

        jdbcClient.sql("""
                select a.id, a.state, d.amount as deposit_amount
                from accounts a
                join deposits d on d.account_id = a.id
                """)
                .withMasterDetailRef("deposits", "id")
                .query(Account.class)
                .list();
    }

//    public static void main(String[] args) {
//        record Row(Map<String, Object> tuples) { }
//        record Update(List<Row> rows, String... idColumns) {
//            public Map<String, Map<String, Object>> sql() {
//                List<String> list = Arrays.asList(idColumns);
//                AtomicInteger counter = new AtomicInteger(0);
//                return rows.stream().map(row -> {
//                    int index = counter.incrementAndGet();
//                    StringBuilder sql = new StringBuilder("UPDATE users SET ");
//                    row.tuples().keySet().stream()
//                            .filter(key -> !list.contains(key))
//                            .forEach(key -> {
//                                sql.append(key).append(" = :").append(key).append(index).append(", ");
//                            });
//                    sql.delete(sql.length() - 2, sql.length());
//                    sql.append(" WHERE ");
//                    String whereClause = list.stream().map(key -> key + " = :" + key + index)
//                            .collect(Collectors.joining(" AND "));
//                    sql.append(whereClause);
//                    Map<String, Object> indexedMap = row.tuples().entrySet().stream()
//                            .map(entry -> Map.entry(entry.getKey() + index, entry.getValue()))
//                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
//                    return Map.entry(sql.toString(), indexedMap);
//                }).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
//            }
//        }
//        List<Row> rows = List.of(
//                new Row(Map.of("id", 1, "name", "John")),
//                new Row(Map.of("id", 2, "name", "Jane")),
//                new Row(Map.of("id", 3, "name", "Alice"))
//        );
//        new Update(rows, "id").sql()
//                .forEach((sql, params) -> System.out.println(sql + " " + params));
//    }
public static void main(String[] args) {
    record Detail(BigDecimal quantity, BigDecimal amount) {}
    record Master(Integer id, Collection<Detail> details) {}

    List<Map<String, Object>> rows = List.of(
            Map.of("id", 1, "detail_amount", new BigDecimal(100), "detail_quantity", new BigDecimal(1)),
            Map.of("id", 1, "detail_amount", new BigDecimal(200), "detail_quantity", new BigDecimal(2)),
            Map.of("id", 2, "detail_amount", new BigDecimal(300), "detail_quantity", new BigDecimal(3))
    );

    record Mapper<T>(String prefix, Class<T> mappedClass) implements Function<Map<String, Object>, T> {
        @Override
        public T apply(Map<String, Object> rows) {
            Constructor<T> constructor = BeanUtils.getResolvableConstructor(mappedClass);
            String[] parameterNames = BeanUtils.getParameterNames(constructor);
            int paramCount = constructor.getParameterCount();
            Object[] args = new Object[paramCount];
            for (int i = 0; i < paramCount; i++) {
                String parameterName = parameterNames[i];
                var base = prefix.isEmpty() ? prefix: prefix + "_";
                args[i] = rows.get(base + parameterName);
            }
            return BeanUtils.instantiateClass(constructor, args);
        }
    }

    UnaryOperator<String> singularize = s -> s.endsWith("s") && s.length() > 1
            ? s.substring(0, s.length()-1)
            : s;

    Function<Method, ? extends Class<?>> extractGenericType = getter -> {
        Type rt = getter.getGenericReturnType();
        if (rt instanceof ParameterizedType p) {
            Type arg = p.getActualTypeArguments()[0];
            if (arg instanceof Class<?>) {
                return (Class<?>) arg;
            }
        }
        throw new IllegalStateException("Cannot resolve generic type of " + getter);
    };
    record Path(String from, String to) { }
    record MapperRef(Path path, Mapper<?> mapper) { }
    Function<Class <?>, Collection<MapperRef>> getMappers = type -> {
        Mapper<?> mapper = new Mapper<>("", type);
        var children = Arrays.stream(type.getRecordComponents())
                .filter(rc -> Collection.class.isAssignableFrom(rc.getType()))
                .map(rc -> {
                    try {
                        PropertyDescriptor pd = new PropertyDescriptor(rc.getName(), type, rc.getName(), null);
                        String prefix = singularize.apply(rc.getName());
                        Mapper<?> child = new Mapper<>(prefix, extractGenericType.apply(pd.getReadMethod()));
                        return new MapperRef(new Path("", prefix), child);
                    } catch (IntrospectionException e) {
                        throw new RuntimeException(e);
                    }
                });
        return Stream.concat(Stream.of(new MapperRef(new Path("", ""), mapper)), children)
                .toList();
    };

    Collection<MapperRef> mappers = getMappers.apply(Master.class);
    record MappedRow(Path path, Map<String, Object> row, Object mapped) { }
    Map<String, String> masterDetailRefMap = Map.of("detail", "id");
    record Id(String prefix, Object value) { }

    Map<Id, Object> refMap = new HashMap<>();
    List<MappedRow> mapped = rows.stream()
            .flatMap(row-> mappers.stream().map(mapperRef-> {
                Path path = mapperRef.path;
                Mapper<?> mapper = mapperRef.mapper;
                Object apply = mapper.apply(row);
                masterDetailRefMap.forEach((_, value)-> {
                    Object ref = row.get(value);
                    Id from = new Id(path.from, ref);
                    Id to = new Id(path.to, ref);
                    refMap.putIfAbsent(from, apply);
                    refMap.putIfAbsent(to, apply);
                });
                return new MappedRow(path, row, apply);
            })).toList();

    Map<Object, List<Object>> groupedMappings = mapped.stream().<Map.Entry<Object, Object>>mapMulti((mappedRow, consumer) -> {
        String prefix = masterDetailRefMap.get(mappedRow.path.to);
        if (prefix != null) {
            Object object = mappedRow.row.get(prefix);
            Object ref = refMap.get(new Id(mappedRow.path.from, object));
            consumer.accept(Map.entry(ref, mappedRow.mapped));
        }
    }).collect(Collectors.groupingBy(Map.Entry::getKey, Collectors.mapping(Map.Entry::getValue, Collectors.toList())));

    System.out.println(groupedMappings);
}
}
