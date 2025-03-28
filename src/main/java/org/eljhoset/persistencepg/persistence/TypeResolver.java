package org.eljhoset.persistencepg.persistence;

import java.util.Map;

public record TypeResolver(Map<Class<?>, Class<?>> byType, Map<String, Class<?>> byProperty) {
    public static TypeResolver empty() {
        return new TypeResolver(Map.of(), Map.of());
    }
    public Class<?> getByTypeByProperty(String property) {
        return byProperty.get(property);
    }
    public Class<?> getByTypeByClass(Class<?> aClass) {
        return byType.get(aClass);
    }
}
