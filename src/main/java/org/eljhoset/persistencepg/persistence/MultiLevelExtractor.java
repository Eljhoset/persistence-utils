package org.eljhoset.persistencepg.persistence;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.lang.NonNull;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

record MultiLevelExtractor<M>(Map<String, String> masterKeyMap,
                              Collection<RowMapperRef> rowMapperRefs) implements ResultSetExtractor<Collection<M>> {
    @Override
    public Collection<M> extractData(@NonNull ResultSet rs) throws DataAccessException {
        Map<Id, Object> refMap = new HashMap<>();
        var list = Stream.iterate(0, i -> nextRow(rs), i -> i + 1)
                .flatMap(_ -> rowMapperRefs.stream().map(nestedRowMapper -> mapRow(rs, nestedRowMapper, refMap)))
                .toList();
        Map<Object, List<Object>> groupedList = list.stream().<Map.Entry<Object, Object>>mapMulti((mappedRow, consumer) -> {
            String prefix = masterKeyMap.get(mappedRow.path.to());
            if (prefix != null) {
                Object object = mappedRow.row.get(prefix);
                Object ref = refMap.get(new Id(mappedRow.path.from(), object));
                consumer.accept(Map.entry(ref, mappedRow.mapped));
            }
        }).collect(Collectors.groupingBy(Map.Entry::getKey, Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
        System.out.println(groupedList);
        return List.of();
    }

    private MappedRow mapRow(ResultSet rs, RowMapperRef mapperRef, Map<Id, Object> refMap) {
        try {
            Path path = mapperRef.path();
            var mapper = mapperRef.rowMapper();
            var row = NestedRowMapper.extractColumnValues(rs);
            Object object = mapper.mapRow(row);
            masterKeyMap.forEach((_, value) -> {
                Object ref = row.get(value);
                Id from = new Id(path.from(), ref);
                Id to = new Id(path.to(), ref);
                refMap.putIfAbsent(from, object);
                refMap.putIfAbsent(to, object);
            });
            return new MappedRow(path, row, object);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    record Id(String prefix, Object value) { }

    record MappedRow(Path path, Map<String, Object> row, Object mapped) { }

    private static boolean nextRow(ResultSet rs) {
        try {
            return rs.next();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
