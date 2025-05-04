package org.eljhoset.persistencepg.persistence;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.lang.NonNull;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Constructor;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

record MultiLevelExtractor<M>(Map<String, String> masterKeyMap,
                              Collection<RowMapperRef> rowMapperRefs) implements ResultSetExtractor<List<M>> {
    @Override
    @SuppressWarnings("unchecked")
    public List<M> extractData(@NonNull ResultSet rs) throws DataAccessException {
        Map<Id, Object> refMap = new HashMap<>();
        var list = Stream.iterate(0, _ -> nextRow(rs), i -> i + 1)
                .flatMap(_ -> rowMapperRefs.stream().map(nestedRowMapper -> mapRow(rs, nestedRowMapper, refMap)))
                .toList();
        var groupedList = list.stream().<Map.Entry<ObjectRef, Object>>mapMulti((mappedRow, consumer) -> {
            String prefix = masterKeyMap.get(mappedRow.path.to());
            if (prefix != null) {
                Object object = mappedRow.row.get(prefix);
                Object ref = refMap.get(new Id(mappedRow.path.from(), object));
                ObjectRef objectRef = new ObjectRef(ref, mappedRow.propertyDescriptor);
                consumer.accept(Map.entry(objectRef, mappedRow.mapped));
            }
        }).collect(Collectors.groupingBy(Map.Entry::getKey, Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
        return (List<M>) getObjects(groupedList);
    }
    private static List<?> getObjects(Map<ObjectRef, List<Object>> groupedList) {
        return groupedList.entrySet().stream().map(entry -> {
            ObjectRef objectRef = entry.getKey();
            PropertyDescriptor pd = objectRef.propertyDescriptor;
            Object original = objectRef.object();
            Class<?> recordType = original.getClass();
            Constructor<?> ctor = BeanUtils.getResolvableConstructor(recordType);
            String[] paramNames = BeanUtils.getParameterNames(ctor);
            Object[] recordArgs = new Object[paramNames.length];
            BeanWrapperImpl reader = new BeanWrapperImpl(original);
            for (int i = 0; i < paramNames.length; i++) {
                String name = paramNames[i];
                if (pd.getName().equals(name)) {
                    recordArgs[i] = entry.getValue();
                } else {
                    recordArgs[i] = reader.getPropertyValue(name);
                }
            }
            return BeanUtils.instantiateClass(ctor, recordArgs);
        }).toList();
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
            return new MappedRow(path, mapperRef.propertyDescriptor(), row, object);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }
    private static boolean nextRow(ResultSet rs) {
        try {
            return rs.next();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }
    private record Id(String prefix, Object value) { }
    private record MappedRow(Path path, PropertyDescriptor propertyDescriptor, Map<String, Object> row, Object mapped) { }
    private record ObjectRef(Object object, PropertyDescriptor propertyDescriptor) { }
}
