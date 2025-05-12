package org.eljhoset.persistencepg.persistence;

import lombok.With;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.PropertyAccessorFactory;
import org.springframework.jdbc.support.JdbcUtils;

import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record NestedMapper<T>(Class<T> mappedClass, MapperTypeConverter typeConverter,
                              Map<Integer, TypeResolver> typeResolvers, @With String prefix) {
    public static <T> NestedMapper<T> newInstance(Class<T> mappedClass, MapperTypeConverter typeConverter, Map<Integer, TypeResolver> typeResolvers) {
        return new NestedMapper<>(mappedClass, typeConverter, typeResolvers, "");
    }
    public static <T> NestedMapper<T> newInstance(Class<T> mappedClass) {
        return newInstance(mappedClass, MapperTypeConverter.noop(), Map.of());
    }
    private static boolean isCollection(Class<?> type) {
        return Collection.class.isAssignableFrom(type);
    }

    private static Class<?> extractGenericType(Method getter) {
        Type rt = getter.getGenericReturnType();
        if (rt instanceof ParameterizedType p) {
            Type arg = p.getActualTypeArguments()[0];
            if (arg instanceof Class<?>) {
                return (Class<?>) arg;
            }
        }
        throw new IllegalStateException("Cannot resolve generic type of " + getter);
    }

    public T map(RowEntry row) {
        return map(mappedClass, row, prefix);
    }

    /**
     * Maps the given Map into an instance of the specified class.
     * <p>
     * If the target type is a record, the mapping is performed via its canonical constructor;
     * otherwise, a default instance is created and its properties are set via a BeanWrapper.
     * </p>
     */
    private <R> R map(Class<R> mappedClass, RowEntry map, String prefix) {
        if (!mappedClass.isRecord()) {
            // For traditional classes, instantiate and then set properties.
            return mapToClass(mappedClass, map, prefix);
        } else {
            // For records, use the canonical constructor.
            return mapToRecord(mappedClass, map, prefix);
        }
    }

    private <R> R mapToClass(Class<R> mappedClass, RowEntry map, String prefix) {
        R instance = BeanUtils.instantiateClass(mappedClass);
        BeanWrapper wrapper = PropertyAccessorFactory.forBeanPropertyAccess(instance);
        for (PropertyDescriptor pd : BeanUtils.getPropertyDescriptors(mappedClass)) {
            if (pd.getWriteMethod() != null) {
                String propertyName = pd.getName();
                Class<?> propertyType = getTypeResolverByTypeByProperty(map.rowNumber(), propertyName);
                Object value;
                if (propertyType != null) {
                    value = map(propertyType, map, appendToPrefix(prefix, propertyName));
                } else {
                    value = resolvePropertyValue(map, propertyName, pd.getPropertyType());
                }
                if (value != null) {
                    wrapper.setPropertyValue(propertyName, value);
                }
            }
        }
        return instance;
    }

    private Class<?> getTypeResolverByTypeByProperty(Integer rowNumber, String propertyName) {
        var typeResolver = typeResolvers.get(rowNumber);
        if (typeResolver == null) {
            return null;
        }
        Class<?> type = typeResolver.getByTypeByProperty(propertyName);
        if (type == null) {
            return null;
        }
        Class<?> typeByClass = typeResolver.getByTypeByClass(type);
        if (typeByClass != null) {
            return typeByClass;
        }
        return type;
    }
    @SuppressWarnings("unchecked")
    private <R> R mapToRecord(Class<R> mappedClass, RowEntry map, String prefix) {
        Constructor<R> constructor = BeanUtils.getResolvableConstructor(mappedClass);
        String[] parameterNames = BeanUtils.getParameterNames(constructor);
        int paramCount = constructor.getParameterCount();
        Object[] args = new Object[paramCount];
        Class<?>[] paramTypes = constructor.getParameterTypes();
        for (int i = 0; i < paramCount; i++) {
            String parameterName = parameterNames[i];
            Class<?> typeFromMap = getTypeResolverByTypeByProperty(map.rowNumber(), parameterName);
            String propertyName = appendToPrefix(prefix, parameterName);
            if (typeFromMap != null) {
                args[i] = map(typeFromMap, map, propertyName);
                continue;
            }
            Class<?> paramType = paramTypes[i];
            Object object = null;
            if (isCollection(paramType)){
                try {
                    PropertyDescriptor pd = new PropertyDescriptor(parameterName, mappedClass, parameterName, null);
                    Class<?> genericType = extractGenericType(pd.getReadMethod());
                    List<RowEntry> list = (List<RowEntry>) map.get(parameterName);
                    if (list!=null){
                        object = list.stream().map(it->map(genericType, it, propertyName))
                                .toList();
                    }
                } catch (IntrospectionException e) {
                    throw new IllegalStateException(e);
                }
            }
            if (object == null) {
                object = resolvePropertyValue(map, propertyName, paramType);
            }
            args[i] = object;
        }
        return BeanUtils.instantiateClass(constructor, args);
    }

    private static String appendToPrefix(String prefix, String parameterName) {
        StringBuilder prefixBuilder = new StringBuilder(prefix);
        if (prefixBuilder.isEmpty()) {
            prefixBuilder = new StringBuilder(parameterName);
        } else {
            prefixBuilder.append("_").append(parameterName);
        }
        return prefixBuilder.toString();
    }

    /**
     * Resolves a property value from the map by first checking for a direct value (using the given
     * property name, its snake_case, and camelCase variants), and if none is found, looking for any
     * nested keys that start with "propertyName_".
     *
     * @param map          the source map
     * @param propertyName the name of the property to resolve
     * @param propertyType the type to convert the value to
     * @return the converted value or {@code null} if no matching value is found
     */
    private Object resolvePropertyValue(RowEntry map,
                                String propertyName, Class<?> propertyType) {

        // Try direct property name.
        if (map.containsKey(propertyName)) {
            return typeConverter.convertIfNecessary(map.get(propertyName), propertyType);
        }
        // Try the snake_case version.
        String snakeCase = JdbcUtils.convertPropertyNameToUnderscoreName(propertyName);
        if (map.containsKey(snakeCase)) {
            return typeConverter.convertIfNecessary(map.get(snakeCase), propertyType);
        }
        // Try the camelCase version.
        String camelCase = JdbcUtils.convertUnderscoreNameToPropertyName(propertyName);
        if (map.containsKey(camelCase)) {
            return typeConverter.convertIfNecessary(map.get(camelCase), propertyType);
        }
        // Look for nested properties (e.g. "account_balance_value" for property "account").
        RowEntry nested = getNestedMap(map, propertyName);
        if (!nested.isEmpty()) {
            // recurse with *this* propertyName as the new prefix
            return map(propertyType, nested, propertyName);
        }
        return null;
    }

    private static RowEntry getNestedMap(RowEntry map, String propertyName) {
        Map<String, Object> nested = new HashMap<>();
        String nestedPrefix = propertyName + "_";
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith(nestedPrefix)) {
                Object value = entry.getValue();
                // 1) stripped key, for id/departments lookups
                nested.put(key.substring(nestedPrefix.length()), value);
                // 2) full key for prefixed lookups like "details_product_id"
                nested.put(key, value);
            }
        }
        return RowEntry.of(map.rowNumber(), nested);
    }
}