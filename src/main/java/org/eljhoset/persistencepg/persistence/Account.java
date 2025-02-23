package org.eljhoset.persistencepg.persistence;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.DynamicUpdate;

import java.math.BigDecimal;
import java.util.Currency;

@Data
@Entity
@DynamicUpdate
@Table(name = "accounts")
@EqualsAndHashCode(of = "id")
public class Account {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @AttributeOverride(name = "value", column = @Column(name = "balance"))
    @AttributeOverride(name = "currency", column = @Column(name = "currency"))
    private Balance balance;
    @Enumerated(EnumType.STRING)
    private State state;

    public enum State {
        ACTIVE, CLOSED, FROZEN
    }
    @Embeddable
    public record Balance(BigDecimal value, Currency currency) {}
}
