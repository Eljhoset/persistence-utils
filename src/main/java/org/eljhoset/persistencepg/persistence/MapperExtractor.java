package org.eljhoset.persistencepg.persistence;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.lang.NonNull;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

record MapperExtractor<T>(Class<T> mappedClass, MapperTypeConverter converter, TypeResolver typeResolver, Map<String, String> groupingRules) implements ResultSetExtractor<List<T>> {
    @Override
    public List<T> extractData(@NonNull ResultSet rs) throws DataAccessException, SQLException {
        var rows = new ArrayList<Map<String, Object>>();
        while (rs.next()){
            var row = ResultSetUtils.extractColumnValues(rs);
            rows.add(row);
        }
        var grouped = NestedGrouper.groupRows(rows, groupingRules);
        NestedMapper<T> mapper = NestedMapper.newInstance(mappedClass, converter, typeResolver);
        return grouped.stream().map(mapper::map).toList();
    }
}
