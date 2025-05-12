package org.eljhoset.persistencepg.persistence;

import org.springframework.beans.BeanWrapperImpl;
import org.springframework.core.convert.ConversionService;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.lang.NonNull;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

record MapperExtractor<T>(Class<T> mappedClass, MapperTypeConverter converter, TypeResolver typeResolver, Map<String, String> groupingRules) implements ResultSetExtractor<List<T>> {

    static <T> MapperExtractor<T> newInstance(Class<T> mappedClass, ConversionService conversionService, Map<String, String> groupingRules) {
        return newInstance(mappedClass, conversionService, groupingRules, TypeResolver.empty());
    }
    static <T> MapperExtractor<T> newInstance(Class<T> mappedClass, ConversionService conversionService, Map<String, String> groupingRules, TypeResolver typeResolver) {
        BeanWrapperImpl tc = new BeanWrapperImpl();
        tc.setConversionService(conversionService);
        MapperTypeConverter converter = tc::convertIfNecessary;
        return new MapperExtractor<>(mappedClass, converter, typeResolver, groupingRules);
    }

    @Override
    public List<T> extractData(@NonNull ResultSet rs) throws DataAccessException, SQLException {
        var rows = new ArrayList<RowEntry>();
        int index = 0;
        while (rs.next()){
            var row = ResultSetUtils.extractColumnValues(index++, rs);
            rows.add(row);
        }
        var grouped = NestedGrouper.groupRows(rows, groupingRules);
        NestedMapper<T> mapper = NestedMapper.newInstance(mappedClass, converter, typeResolver);
        return grouped.stream().map(mapper::map).toList();
    }
}
