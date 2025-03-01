package org.eljhoset.persistencepg.persistence;

import lombok.experimental.Delegate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.PropertyAccessorFactory;
import org.springframework.core.convert.ConversionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.SingleColumnRowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.lang.NonNull;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;
import java.util.stream.StreamSupport;

public class ConversionAwareStatementSpec implements JdbcClient.StatementSpec {
    private final String sql;
    private final JdbcClient jdbcClient;
    private final @Delegate JdbcClient.StatementSpec delegate;
    private final ConversionService conversionService;
    private final Map<String, Object> params = new HashMap<>();

    private static final Class<?>[] SCALAR_TYPES = {
            Number.class, Boolean.class, String.class, Character.class, LocalDateTime.class
    };

    public ConversionAwareStatementSpec(String sql, JdbcClient jdbcClient, ConversionService conversionService) {
        this.sql = sql;
        this.jdbcClient = jdbcClient;
        this.conversionService = conversionService;
        this.delegate = jdbcClient.sql(sql);
    }

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
        params.put(name, value);
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

    public <T> @NonNull Page<T> query(@NonNull Class<T> resultType, Pageable pageable) {

        StringBuilder sqlBuilder = new StringBuilder(this.sql);
        pageable.getSort().stream().forEach(order -> {
            String property = order.getProperty();
            String direction = order.getDirection().name();
            sqlBuilder.append(" ORDER BY ").append(property).append(" ").append(direction);
        });
        sqlBuilder.append(" LIMIT ").append(":limit").append(" OFFSET ").append(":offset");

        var listSpec = jdbcClient.sql(sqlBuilder.toString())
                .param("limit", pageable.getPageSize())
                .param("offset", pageable.getOffset());
        params.forEach(listSpec::param);

        LongSupplier total = () -> {
            String fromClause = JdbcPaginationUtil.extractFromClause(this.sql);
            String countSql = "SELECT COUNT(*) " + fromClause;
            JdbcClient.StatementSpec countSpec = jdbcClient.sql(countSql);
            params.forEach(countSpec::param);
            return countSpec.query(Long.class).single();
        };

        List<T> list = listSpec.query(resultType).list();
        return PageableExecutionUtils.getPage(list, pageable, total);
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
