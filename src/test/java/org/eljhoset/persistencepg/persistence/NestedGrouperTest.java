package org.eljhoset.persistencepg.persistence;

import org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

class NestedGrouperTest {
    static Stream<Arguments> groupingTestCases() {
        return Stream.of(
                Arguments.of(
                        Named.of("No Grouping", List.of(
                                Map.of("id", 1, "amount", "100"),
                                Map.of("id", 2, "amount", "200")
                        )),
                        Map.of(),
                        List.of(
                                Map.of("id", 1, "amount", "100"),
                                Map.of("id", 2, "amount", "200")
                        )
                ),
                Arguments.of(
                        Named.of("Simple Grouping", List.of(
                                Map.of("id", 1, "amount", "100", "details_quantity", 1),
                                Map.of("id", 1, "amount", "100", "details_quantity", 2),
                                Map.of("id", 2, "amount", "200", "details_quantity", 1)
                        )),
                        Map.of("details", "id"),
                        List.of(
                                Map.of("id", 1, "amount", "100", "details", List.of(
                                        Map.of("details_quantity", 1),
                                        Map.of("details_quantity", 2)
                                )),
                                Map.of("id", 2, "amount", "200", "details", List.of(
                                        Map.of("details_quantity", 1)
                                ))
                        )
                ),
                Arguments.of(
                        Named.of("Simple Grouping multiple properties",List.of(
                                Map.of("id", 1, "amount", "100", "details_quantity", 1, "details_name", "a"),
                                Map.of("id", 1, "amount", "100", "details_quantity", 2, "details_name", "b"),
                                Map.of("id", 2, "amount", "200", "details_quantity", 1, "details_name", "c")
                        )),
                        Map.of("details", "id"),
                        List.of(
                                Map.of("id", 1, "amount", "100", "details", List.of(
                                        Map.of("details_quantity", 1, "details_name", "a"),
                                        Map.of("details_quantity", 2, "details_name", "b")
                                )),
                                Map.of("id", 2, "amount", "200", "details", List.of(
                                        Map.of("details_quantity", 1, "details_name", "c")
                                ))
                        )
                ),
                Arguments.of(
                        Named.of("Nested Grouping multiple properties", List.of(
                                Map.of("id", 1, "amount", "100", "details_quantity", 1, "details_account_id", 1, "details_account_holders_id", 1, "details_account_holders_name", "John"),
                                Map.of("id", 1, "amount", "100", "details_quantity", 1, "details_account_id", 1, "details_account_holders_id", 1, "details_account_holders_name", "Jane"),
                                Map.of("id", 1, "amount", "100", "details_quantity", 2, "details_account_id", 1, "details_account_holders_id", 2, "details_account_holders_name", "John"),
                                Map.of("id", 1, "amount", "100", "details_quantity", 2, "details_account_id", 1, "details_account_holders_id", 2, "details_account_holders_name", "Jane"),
                                Map.of("id", 2, "amount", "200", "details_quantity", 1, "details_account_id", 1, "details_account_holders_id", 1, "details_account_holders_name", "John"),
                                Map.of("id", 2, "amount", "200", "details_quantity", 1, "details_account_id", 1, "details_account_holders_id", 1, "details_account_holders_name", "Jane"),
                                Map.of("id", 3, "amount", "300", "details_quantity", 1, "details_account_id", 3,  "details_account_holders_id", 3, "details_account_holders_name", "Mike")
                        )),
                        Map.of("details", "id", "details_account_holders", "details_account_id"),
                        List.of(
                                Map.of("id", 1, "amount", "100", "details", List.of(
                                        Map.of("details_quantity", 1, "details_account_id", 1, "details_account_holders", List.of(
                                                Map.of("details_account_holders_id", 1, "details_account_holders_name", "John"),
                                                Map.of("details_account_holders_id", 2, "details_account_holders_name", "Jane")
                                        )),
                                        Map.of("details_quantity", 2, "details_account_id", 1, "details_account_holders", List.of(
                                                Map.of("details_account_holders_id", 1, "details_account_holders_name", "John"),
                                                Map.of("details_account_holders_id", 2, "details_account_holders_name", "Jane")
                                        ))
                                )),
                                Map.of("id", 2, "amount", "200", "details", List.of(
                                        Map.of("details_quantity", 1, "details_account_id", 1, "details_account_holders", List.of(
                                                Map.of("details_account_holders_id", 1, "details_account_holders_name", "John"),
                                                Map.of("details_account_holders_id", 2, "details_account_holders_name", "Jane")
                                        ))
                                )),
                                Map.of("id", 3, "amount", "300", "details", List.of(
                                        Map.of("details_quantity", 1, "details_account_id", 3, "details_account_holders", List.of(
                                                Map.of("details_account_holders_id", 3, "details_account_holders_name", "Mike")
                                        ))
                                ))
                        )
                )
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("groupingTestCases")
    void testGrouping(List<Map<String, Object>> rows, Map<String, String> groupingRules, List<Map<String, Object>> expected) {
        List<Map<String, Object>> actual = NestedGrouper.groupRows(rows, groupingRules);
        // Configure a recursive comparator that ignores order of all collections
        RecursiveComparisonConfiguration config = RecursiveComparisonConfiguration.builder()
                .withIgnoreCollectionOrder(true)
                .build();

        // Assert deep equality without explicitly naming any keys
        assertThat(actual)
                .usingRecursiveComparison(config)
                .isEqualTo(expected);
    }
}