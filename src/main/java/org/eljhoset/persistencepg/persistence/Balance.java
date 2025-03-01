package org.eljhoset.persistencepg.persistence;

import java.math.BigDecimal;
import java.util.Currency;

public record Balance(BigDecimal value, Currency currency) { }
