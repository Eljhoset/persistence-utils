package org.eljhoset.persistencepg.persistence;

import org.springframework.beans.*;
import org.springframework.core.convert.ConversionService;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.JdbcUtils;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Constructor;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public record NestedRowMapper<T>(Class<T> mappedClass, TypeConverter typeConverter, Map<String, Class<?>> typeMap) implements RowMapper<T> {
    public static <T> NestedRowMapper<T> newInstance(
            Class<T> mappedClass, @Nullable ConversionService conversionService) {
        return newInstance(mappedClass, conversionService, Map.of());
    }
    public static <T> NestedRowMapper<T> newInstance(
            Class<T> mappedClass, @Nullable ConversionService conversionService, Map<String, Class<?>> typeMap) {
        BeanWrapperImpl tc = new BeanWrapperImpl();
        tc.setConversionService(conversionService);
        return new NestedRowMapper<>(mappedClass, tc, typeMap);
    }

    @Override
    public T mapRow(@NonNull ResultSet rs, int rowNum) throws SQLException {
        Map<String, Object> mapOfColumnValues = extractColumnValues(rs);
        return map(typeConverter, mappedClass, mapOfColumnValues, typeMap);
    }

    private Map<String, Object> extractColumnValues(ResultSet rs) throws SQLException {
        Map<String, Object> mapOfColumnValues = new HashMap<>();
        ResultSetMetaData rsmd = rs.getMetaData();
        int columnCount = rsmd.getColumnCount();
        for (int i = 1; i <= columnCount; i++) {
            String column = JdbcUtils.lookupColumnName(rsmd, i);
            Object value = JdbcUtils.getResultSetValue(rs, i);
            mapOfColumnValues.put(column, value);
        }
        return mapOfColumnValues;
    }

    /**
     * Maps the given Map into an instance of the specified class.
     * <p>
     * If the target type is a record, the mapping is performed via its canonical constructor;
     * otherwise, a default instance is created and its properties are set via a BeanWrapper.
     * </p>
     */
    static <T> T map(TypeConverter tc, Class<T> mappedClass, Map<String, Object> map, Map<String, Class<?>> typeMap) {
        if (!mappedClass.isRecord()) {
            // For traditional classes, instantiate and then set properties.
            return mapToClass(tc, mappedClass, map, typeMap);
        } else {
            // For records, use the canonical constructor.
            return mapToRecord(tc, mappedClass, map, typeMap);
        }
    }

    static <T> T mapToClass(TypeConverter tc, Class<T> mappedClass, Map<String, Object> map, Map<String, Class<?>> typeMap) {
        T instance = BeanUtils.instantiateClass(mappedClass);
        BeanWrapper wrapper = PropertyAccessorFactory.forBeanPropertyAccess(instance);
        for (PropertyDescriptor pd : BeanUtils.getPropertyDescriptors(mappedClass)) {
            if (pd.getWriteMethod() != null) {
                String propertyName = pd.getName();
                Class<?> propertyType = typeMap.get(propertyName);
                Object value;
                if (propertyType != null) {
                    value = map(tc, propertyType, map, typeMap);
                } else {
                    value = resolvePropertyValue(tc, map, propertyName, pd.getPropertyType(), typeMap);
                }
                if (value != null) {
                    wrapper.setPropertyValue(propertyName, value);
                }
            }
        }
        return instance;
    }

    static <T> T mapToRecord(TypeConverter tc, Class<T> mappedClass, Map<String, Object> map, Map<String, Class<?>> typeMap) {
        Constructor<T> constructor = BeanUtils.getResolvableConstructor(mappedClass);
        String[] parameterNames = BeanUtils.getParameterNames(constructor);
        int paramCount = constructor.getParameterCount();
        Object[] args = new Object[paramCount];
        Class<?>[] paramTypes = constructor.getParameterTypes();
        for (int i = 0; i < paramCount; i++) {
            String parameterName = parameterNames[i];
            Class<?> typeFromMap = typeMap.get(parameterName);
            if (typeFromMap != null) {
                args[i] = map(tc, typeFromMap, map, typeMap);
                continue;
            }
            args[i] = resolvePropertyValue(tc, map, parameterName, paramTypes[i], typeMap);
        }
        return BeanUtils.instantiateClass(constructor, args);
    }

    /**
     * Resolves a property value from the map by first checking for a direct value (using the given
     * property name, its snake_case, and camelCase variants), and if none is found, looking for any
     * nested keys that start with "propertyName_".
     *
     * @param tc           the TypeConverter to perform conversion
     * @param map          the source map
     * @param propertyName the name of the property to resolve
     * @param propertyType the type to convert the value to
     * @return the converted value or {@code null} if no matching value is found
     */
    static Object resolvePropertyValue(TypeConverter tc, Map<String, Object> map,
                                       String propertyName, Class<?> propertyType, Map<String, Class<?>> typeMap) {

        // Try direct property name.
        if (map.containsKey(propertyName)) {
            return tc.convertIfNecessary(map.get(propertyName), propertyType);
        }
        // Try snake_case version.
        String snakeCase = JdbcUtils.convertPropertyNameToUnderscoreName(propertyName);
        if (map.containsKey(snakeCase)) {
            return tc.convertIfNecessary(map.get(snakeCase), propertyType);
        }
        // Try camelCase version.
        String camelCase = JdbcUtils.convertUnderscoreNameToPropertyName(propertyName);
        if (map.containsKey(camelCase)) {
            return tc.convertIfNecessary(map.get(camelCase), propertyType);
        }
        // Look for nested properties (e.g. "account_balance_value" for property "account").
        Map<String, Object> nested = new HashMap<>();
        String prefix = propertyName + "_";
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                // Remove the prefix.
                nested.put(entry.getKey().substring(prefix.length()), entry.getValue());
            }
        }
        if (!nested.isEmpty()) {
            return map(tc, propertyType, nested, typeMap);
        }
        return null;
    }
}
