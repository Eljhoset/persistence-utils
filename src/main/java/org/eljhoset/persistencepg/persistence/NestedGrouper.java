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
        // 1) Find the “root” grouping name (the one with no '_' in it)
        Optional<String> rootOpt = groupingRules.keySet().stream()
                .filter(k -> !k.contains("_"))
                .findFirst();

        // If there is no root rule, just return the rows unchanged
        if (rootOpt.isEmpty()) {
            return rows.stream()
                    .map(entry -> new RowEntry(entry.rowNumber(), new HashMap<>(entry.values())))
                    .toList();
        }
        String rootName = rootOpt.get();
        String rootProp = groupingRules.get(rootName);

        // 2) Partition all rows by the root grouping column
        Map<Object, List<RowEntry>> rootBuckets = rows.stream()
                .collect(Collectors.groupingBy(e -> e.values().get(rootProp)));

        List<RowEntry> result = new ArrayList<>();

        // 3) For each root‐group:
        for (List<RowEntry> bucket : rootBuckets.values()) {
            RowEntry first = bucket.getFirst();
            Map<String, Object> sample = first.values();
            Map<String, Object> rootMap = new HashMap<>();

            // 3a) copy every field not belonging to "details_" (or deeper) into the root map
            sample.entrySet().stream()
                    .filter(e -> !e.getKey().startsWith(rootName + "_"))
                    .forEach(e -> rootMap.put(e.getKey(), e.getValue()));

            // 3b) build the "details" list
            List<RowEntry> detailsList = buildLevel(
                    bucket,                     // all rows for this root
                    rootName,                   // the list we’re building now
                    groupingRules
            );

            rootMap.put(rootName, detailsList);
            result.add(new RowEntry(first.rowNumber(), rootMap));
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
        Set<String> myKeys = rows.getFirst().values().keySet().stream()
                .filter(k -> k.startsWith(myPrefix))
                .filter(k -> allDeeperPrefixes.stream().noneMatch(k::startsWith))
                .collect(Collectors.toSet());

        // 4) group by the UNIQUE combination of those keys
        Map<List<Object>, List<RowEntry>> buckets = rows.stream()
                .collect(Collectors.groupingBy(entry ->
                        myKeys.stream().sorted()
                                .map(key -> entry.values().get(key))
                                .toList()
                ));

        List<RowEntry> out = new ArrayList<>();

        for (List<RowEntry> bucket : buckets.values()) {
            RowEntry first = bucket.getFirst();
            Map<String, Object> sample = first.values();
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
