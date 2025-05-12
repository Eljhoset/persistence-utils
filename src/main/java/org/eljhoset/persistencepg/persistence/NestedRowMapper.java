package org.eljhoset.persistencepg.persistence;

import lombok.With;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.TypeConverter;
import org.springframework.core.convert.ConversionService;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

public record NestedRowMapper<T>(Class<T> mappedClass, TypeConverter typeConverter,
                                 Map<Integer, TypeResolver> typeResolvers, @With String prefix) implements RowMapper<T> {
    public static <T> NestedRowMapper<T> newInstance(
            Class<T> mappedClass, @Nullable ConversionService conversionService) {
        return newInstance(mappedClass, conversionService, Map.of());
    }

    public static <T> NestedRowMapper<T> newInstance(
            Class<T> mappedClass, @Nullable ConversionService conversionService, Map<Integer, TypeResolver> typeResolvers) {
        BeanWrapperImpl tc = new BeanWrapperImpl();
        tc.setConversionService(conversionService);
        return new NestedRowMapper<>(mappedClass, tc, typeResolvers, "");
    }

    @Override
    public T mapRow(@NonNull ResultSet rs, int rowNum) throws SQLException {
        RowEntry mapOfColumnValues = ResultSetUtils.extractColumnValues(rowNum, rs);
        MapperTypeConverter converter = typeConverter::convertIfNecessary;
        return NestedMapper.newInstance(mappedClass, converter, typeResolvers).map(mapOfColumnValues);
    }
}
