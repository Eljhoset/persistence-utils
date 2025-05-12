package org.eljhoset.persistencepg.persistence;

import org.springframework.beans.BeanWrapperImpl;
import org.springframework.core.convert.ConversionService;

import java.util.List;
import java.util.Map;

record MapperExtractorDelegate<T>(Class<T> mappedClass, ConversionService conversionService, List<RowEntry> rows,
                                  Map<Integer, TypeResolver> typeResolvers, Map<String, String> groupingRules) {
    public List<T> extractData() {
        BeanWrapperImpl tc = new BeanWrapperImpl();
        tc.setConversionService(conversionService);
        MapperTypeConverter converter = tc::convertIfNecessary;
        var grouped = NestedGrouper.groupRows(rows, groupingRules);
        NestedMapper<T> mapper = NestedMapper.newInstance(mappedClass, converter, typeResolvers);
        return grouped.stream().map(mapper::map).toList();
    }
}
