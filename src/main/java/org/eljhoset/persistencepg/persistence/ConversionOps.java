package org.eljhoset.persistencepg.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.ConversionService;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.stream.StreamSupport;

@RequiredArgsConstructor
class ConversionOps {
    private final ConversionService conversionService;
    private static final Class<?>[] SCALAR_TYPES = {
            Number.class, Boolean.class, String.class, Character.class, LocalDateTime.class
    };

    public static boolean isScalarType(Class<?> type) {
        return type.isPrimitive()
               || Arrays.stream(SCALAR_TYPES).anyMatch(type::isAssignableFrom);
    }

    public Object checkAndConvert(Object value) {
        if (value instanceof Iterable<?> iterable) {
            return StreamSupport.stream(iterable.spliterator(), false)
                    .map(element -> {
                        if (element == null) return null;
                        return convert(element);
                    }).toList();
        }
        if (value.getClass().isArray()) {
            Object[] array = (Object[]) value;
            return Arrays.stream(array)
                    .map(element -> {
                        if (element == null) return null;
                        return convert(element);
                    }).toArray();
        }
        return convert(value);
    }

    private Object convert(Object value) {
        Class<?> sourceType = value.getClass();
        if (isScalarType(sourceType)) {
            return value;
        }
        for (Class<?> scalarType : SCALAR_TYPES) {
            if (conversionService.canConvert(sourceType, scalarType)) {
                return conversionService.convert(value, scalarType);
            }
        }
        return value;
    }
}
