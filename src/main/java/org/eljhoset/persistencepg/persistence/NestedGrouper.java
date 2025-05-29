package org.eljhoset.persistencepg.persistence;

import lombok.experimental.UtilityClass;

import java.util.*;
import java.util.stream.Collectors;

@UtilityClass
class NestedGrouper {

    /**
     * Recursively groups a flat list of rows into nested List<Map> structures
     * according to the supplied groupingRules.
     *
     * @param rows          The flat list of maps from columnName→value
     * @param groupingRules Map of nestedListName→groupingColumn
     * @return A List of Maps with the nested lists applied
     */
    public static List<RowEntry> groupRows(
            List<RowEntry> rows,
            Map<String, String> groupingRules
    ) {
        // 1) figure out which rule-names are true “roots”
        Set<String> keys     = groupingRules.keySet();
        List<String> roots = keys.stream()
                .filter(k -> keys.stream()
                        .filter(p -> !p.equals(k))
                        .noneMatch(p -> k.startsWith(p + "_")))
                .toList();

        if (roots.isEmpty()) {
            // no grouping rules: just clone your rows
            return rows.stream()
                    .map(e -> new RowEntry(e.rowNumber(), new HashMap<>(e.row())))
                    .toList();
        }

        // 2) group by the composite of all root grouping-columns
        Map<List<Object>, List<RowEntry>> buckets = rows.stream()
                .collect(Collectors.groupingBy(e ->
                        roots.stream()
                                .map(r -> e.row().get(groupingRules.get(r)))
                                .toList()
                ));

        List<RowEntry> result = new ArrayList<>();

        // 3) for each unique combination…
        for (List<RowEntry> bucket : buckets.values()) {
            RowEntry first = bucket.getFirst();
            Map<String, Object> sample = first.row();
            Map<String, Object> out     = new HashMap<>();

            // 3a) copy any field that isn’t under any rootName_… prefix
            sample.forEach((col, val) -> {
                boolean isUnderAnyRoot = roots.stream()
                        .anyMatch(r -> col.startsWith(r + "_"));
                if (!isUnderAnyRoot) {
                    out.put(col, val);
                }
            });

            // 3b) for each root, build its nested list
            for (String root : roots) {
                List<RowEntry> nested = buildLevel(bucket, root, groupingRules);
                out.put(root, nested);
            }

            result.add(new RowEntry(first.rowNumber(), out));
        }

        return result;
    }

    /**
     * Build one level of nesting (e.g. "details", then inside that "details_account_holders", etc.)
     */
    private static List<RowEntry> buildLevel(
            List<RowEntry> rows,
            String levelName,
            Map<String, String> groupingRules
    ) {
        // 1) find any immediate child rule under this level:
        //    e.g. keys that start with "details_" but are not deeper ("details_account_holders_" is valid)
        List<String> children = groupingRules.keySet().stream()
                .filter(k -> k.startsWith(levelName + "_"))
                .toList();

        // 2) Identify the field‐prefix for this level (e.g. "details_") and for all deeper levels
        String myPrefix = levelName + "_";
        Set<String> allDeeperPrefixes = children.stream()
                .map(k -> k + "_")
                .collect(Collectors.toSet());

        // 3) collect all the keys that belong just to this level
        Set<String> myKeys = rows.getFirst().row().keySet().stream()
                .filter(k -> k.startsWith(myPrefix))
                .filter(k -> allDeeperPrefixes.stream().noneMatch(k::startsWith))
                .collect(Collectors.toSet());

        // 4) group by the UNIQUE combination of those keys
        Map<List<Object>, List<RowEntry>> buckets = rows.stream()
                .collect(Collectors.groupingBy(entry ->
                        myKeys.stream().sorted()
                                .map(key -> entry.row().get(key))
                                .toList()
                ));

        List<RowEntry> out = new ArrayList<>();

        for (List<RowEntry> bucket : buckets.values()) {
            RowEntry first = bucket.getFirst();
            Map<String, Object> sample = first.row();
            Map<String, Object> map = new HashMap<>();

            // 4a) copy each of the level’s keys into this map
            myKeys.stream().sorted().forEach(k -> map.put(k, sample.get(k)));

            // 4b) for each child rule, recurse
            for (String childKey : children) {
                List<RowEntry> childList = buildLevel(bucket, childKey, groupingRules);
                map.put(childKey, childList);
            }

            out.add(new RowEntry(first.rowNumber(), map));
        }

        return out;
    }
}
