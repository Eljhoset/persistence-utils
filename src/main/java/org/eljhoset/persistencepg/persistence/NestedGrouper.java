package org.eljhoset.persistencepg.persistence;

import java.util.*;
import java.util.stream.Collectors;

class NestedGrouper {

    /**
     * Recursively groups a flat list of rows into nested List<Map> structures
     * according to the supplied groupingRules.
     *
     * @param rows          The flat list of maps from columnName→value
     * @param groupingRules Map of nestedListName→groupingColumn
     * @return A List of Maps with the nested lists applied
     */
    public static List<Map<String, Object>> groupRows(
            List<Map<String, Object>> rows,
            Map<String, String> groupingRules
    ) {
        // 1) Find the “root” grouping name (the one with no '_' in it)
        Optional<String> rootOpt = groupingRules.keySet().stream()
                .filter(k -> !k.contains("_"))
                .findFirst();

        // If there is no root rule, just return the rows unchanged
        if (rootOpt.isEmpty()) {
            return new ArrayList<>(rows);
        }
        String rootName = rootOpt.get();
        String rootProp = groupingRules.get(rootName);

        // 2) Partition all rows by the root grouping column
        Map<Object, List<Map<String, Object>>> rootBuckets = rows.stream()
                .collect(Collectors.groupingBy(r -> r.get(rootProp)));

        List<Map<String, Object>> result = new ArrayList<>();

        // 3) For each root‐group:
        for (List<Map<String, Object>> bucket : rootBuckets.values()) {
            Map<String, Object> rootMap = new HashMap<>();
            Map<String, Object> sample = bucket.getFirst();

            // 3a) copy every field not belonging to "details_" (or deeper) into the root map
            for (Map.Entry<String, Object> e : sample.entrySet()) {
                String key = e.getKey();
                if (!key.startsWith(rootName + "_")) {
                    rootMap.put(key, e.getValue());
                }
            }

            // 3b) build the "details" list
            List<Map<String, Object>> detailsList = buildLevel(
                    bucket,                     // all rows for this root
                    rootName,                   // the list we’re building now
                    groupingRules
            );

            rootMap.put(rootName, detailsList);
            result.add(rootMap);
        }

        return result;
    }

    /**
     * Build one level of nesting (e.g. "details", then inside that "details_account_holders", etc.)
     */
    private static List<Map<String, Object>> buildLevel(
            List<Map<String, Object>> rows,
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
        Set<String> myKeys = rows.getFirst().keySet().stream()
                .filter(k -> k.startsWith(myPrefix))
                .filter(k -> allDeeperPrefixes.stream().noneMatch(k::startsWith))
                .collect(Collectors.toSet());

        // 4) group by the UNIQUE combination of those keys
        Map<List<Object>, List<Map<String, Object>>> buckets = rows.stream()
                .collect(Collectors.groupingBy(r ->
                        myKeys.stream()
                                .sorted()
                                .map(r::get)
                                .toList()
                ));

        List<Map<String, Object>> out = new ArrayList<>();

        for (List<Map<String, Object>> bucket : buckets.values()) {
            Map<String, Object> map = new HashMap<>();
            Map<String, Object> sample = bucket.getFirst();

            // 4a) copy each of the level’s keys into this map
            myKeys.stream().sorted().forEach(k ->
                    map.put(k, sample.get(k))
            );

            // 4b) for each child rule, recurse
            for (String childName : children) {
                List<Map<String, Object>> childList = buildLevel(bucket, childName, groupingRules);
                map.put(childName, childList);
            }

            out.add(map);
        }

        return out;
    }
}
