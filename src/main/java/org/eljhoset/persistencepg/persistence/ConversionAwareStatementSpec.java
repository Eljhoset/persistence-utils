package org.eljhoset.persistencepg.persistence;

import lombok.experimental.Delegate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.PropertyAccessorFactory;
import org.springframework.core.convert.ConversionService;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.SingleColumnRowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.lang.NonNull;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.stream.StreamSupport;

public record ConversionAwareStatementSpec(@Delegate JdbcClient.StatementSpec delegate,
                                           ConversionService conversionService) implements JdbcClient.StatementSpec {
    private static final Class<?>[] SCALAR_TYPES = {
            Number.class, Boolean.class, String.class, Character.class, LocalDateTime.class
    };

    private static boolean isScalarType(Class<?> type) {
        return type.isPrimitive()
               || Arrays.stream(SCALAR_TYPES).anyMatch(type::isAssignableFrom);
    }
    @Override
    public @NonNull ConversionAwareStatementSpec param(Object object){
        BeanWrapper beanWrapper = PropertyAccessorFactory.forBeanPropertyAccess(object);
        Arrays.stream(BeanUtils.getPropertyDescriptors(object.getClass())).forEach(e->{
            Object propertyValue = beanWrapper.getPropertyValue(e.getName());
            param(e.getName(), propertyValue);
        });
        return this;
    }
    @Override
    public @NonNull ConversionAwareStatementSpec param(@NonNull String name, Object value) {
        if (value != null) {
            value = checkAndConvert(value);
        }
        delegate.param(name, value);
        return this;
    }

    public <R> PolymorphicSpec<R> columnDiscriminator(String discriminatorColumn) {
        return new PolymorphicSpec<>(conversionService, discriminatorColumn, this);
    }
    public <R> PolymorphicFieldSpec.PolymorphicFieldSpecBuilder<R> columnDiscriminator(String field, String discriminatorColumn) {
        return PolymorphicFieldSpec.newInstance(delegate, conversionService, field, discriminatorColumn);
    }

    public <T> @NonNull JdbcClient.MappedQuerySpec<T> query(@NonNull Class<T> resultType) {
        if (isScalarType(resultType)) {
            var singleColumnMapper = SingleColumnRowMapper.newInstance(resultType);
            return delegate.query(singleColumnMapper);
        } else {
            RowMapper<T> rowMapper = NestedRowMapper.newInstance(resultType, this.conversionService);
            return delegate.query(rowMapper);
        }
    }

    private Object checkAndConvert(Object value) {
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
