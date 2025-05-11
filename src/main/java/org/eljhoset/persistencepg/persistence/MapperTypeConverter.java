package org.eljhoset.persistencepg.persistence;

@FunctionalInterface
public interface MapperTypeConverter {
    static MapperTypeConverter noop() {
        return new MapperTypeConverter() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> T convertIfNecessary(Object value, Class<T> requiredType) {
                return (T) value;
            }
        };
    }
    <T> T convertIfNecessary(Object value, Class<T> requiredType);
}
