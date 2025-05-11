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
                                 TypeResolver typeResolver, @With String prefix) implements RowMapper<T> {
    public static <T> NestedRowMapper<T> newInstance(
            Class<T> mappedClass, @Nullable ConversionService conversionService) {
        return newInstance(mappedClass, conversionService, TypeResolver.empty());
    }

    public static <T> NestedRowMapper<T> newInstance(
            Class<T> mappedClass, @Nullable ConversionService conversionService, TypeResolver typeResolver) {
        BeanWrapperImpl tc = new BeanWrapperImpl();
        tc.setConversionService(conversionService);
        return new NestedRowMapper<>(mappedClass, tc, typeResolver, "");
    }

    @Override
    public T mapRow(@NonNull ResultSet rs, int rowNum) throws SQLException {
        Map<String, Object> mapOfColumnValues = ResultSetUtils.extractColumnValues(rs);
        MapperTypeConverter converter = typeConverter::convertIfNecessary;
        return NestedMapper.newInstance(mappedClass, converter, typeResolver).map(mapOfColumnValues);
    }
}
