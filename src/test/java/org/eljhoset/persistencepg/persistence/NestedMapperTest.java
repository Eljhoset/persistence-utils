package org.eljhoset.persistencepg.persistence;

import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class NestedMapperTest {
    interface Simple {
        record Amount(BigDecimal value, Integer currencyId) { }
        record Department(String name) { }
        record Product(Long id, Department department) { }
        record Detail(Product product, Amount amount, Integer quantity) { }
        record Header(Long id, String type, Detail detail) { }
    }

    interface Groping {
        record Amount(BigDecimal value, Integer currencyId) { }
        record Department(String name) { }
        record Product(Long id, Department department) { }
        record Detail(Product product, Amount amount, Integer quantity) { }
        record Header(Long id, String type, Collection<Detail> details) { }
    }
    interface NestedGroping {
        record Amount(BigDecimal value, Integer currencyId) { }
        record Department(String name) { }
        record Product(Long id, Collection<Department> departments) { }
        record Detail(Product product, Amount amount, Integer quantity) { }
        record Header(Long id, String type, Collection<Detail> details) { }
    }

    static Stream<Arguments> rows() {
        return Stream.of(
                Arguments.of(Named.of("Simple", Map.of(
                                "id", 1L,
                                "type", "WITHDRAWAL",
                                "detail_product_id", 15L,
                                "detail_product_department_name", "IT",
                                "detail_amount_value", BigDecimal.valueOf(100),
                                "detail_amount_currency_id", 1,
                                "detail_quantity", 10
                        )),
                        NestedMapper.newInstance(Simple.Header.class),
                        new Simple.Header(
                                1L,
                                "WITHDRAWAL",
                                new Simple.Detail(
                                        new Simple.Product(
                                                15L,
                                                new Simple.Department("IT")
                                        ),
                                        new Simple.Amount(
                                                BigDecimal.valueOf(100),
                                                1
                                        ),
                                        10
                                )
                        )
                ),
                Arguments.of(Named.of("Grouping",Map.of(
                                "id", 1L,
                                "type", "WITHDRAWAL",
                                "details", List.of(Map.<String, Object>of(
                                        "details_product_id", 15L,
                                        "details_product_department_name", "IT",
                                        "details_amount_value", BigDecimal.valueOf(100),
                                        "details_amount_currency_id", 1,
                                        "details_quantity", 10
                                ))
                        )),
                        NestedMapper.newInstance(Groping.Header.class),
                        new Groping.Header(
                                1L,
                                "WITHDRAWAL",
                                List.of(new Groping.Detail(
                                        new Groping.Product(
                                                15L,
                                                new Groping.Department("IT")
                                        ),
                                        new Groping.Amount(
                                                BigDecimal.valueOf(100),
                                                1
                                        ),
                                        10
                                ))
                        )
                ),
                Arguments.of(Named.of("Nested Grouping",Map.of(
                                "id", 1L,
                                "type", "WITHDRAWAL",
                                "details", List.of(Map.of(
                                        "details_product_id", 15L,
                                        "details_product_departments", List.of(Map.<String, Object>of("details_product_departments_name", "IT")),
                                        "details_amount_value", BigDecimal.valueOf(100),
                                        "details_amount_currency_id", 1,
                                        "details_quantity", 10
                                ))
                        )),
                        NestedMapper.newInstance(NestedGroping.Header.class),
                        new NestedGroping.Header(
                                1L,
                                "WITHDRAWAL",
                                List.of(new NestedGroping.Detail(
                                        new NestedGroping.Product(
                                                15L,
                                                List.of(new NestedGroping.Department("IT"))
                                        ),
                                        new NestedGroping.Amount(
                                                BigDecimal.valueOf(100),
                                                1
                                        ),
                                        10
                                ))
                        )
                )
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rows")
    void testMap(Map<String, Object> row, NestedMapper<?> rowMapper, Object expected) {
        Object actual = rowMapper.map(row);
        assertThat(actual).isEqualTo(expected);
    }

}
