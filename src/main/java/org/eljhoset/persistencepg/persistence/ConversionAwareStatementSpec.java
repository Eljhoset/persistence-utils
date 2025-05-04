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
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.lang.NonNull;

import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.function.LongSupplier;
import java.util.stream.Stream;

public class ConversionAwareStatementSpec implements JdbcClient.StatementSpec {
    private final String sql;
    private final JdbcClient jdbcClient;
    private final @Delegate JdbcClient.StatementSpec delegate;
    private final ConversionService conversionService;
    private final ConversionOps conversionOps;
    private final PolymorphicFieldSpec polymorphicFieldSpec;
    private final Map<String, Object> params = new HashMap<>();
    private final Map<String, String> masterDetailRefMap = new HashMap<>();

    public ConversionAwareStatementSpec(String sql, JdbcClient jdbcClient, ConversionService conversionService) {
        this.sql = sql;
        this.jdbcClient = jdbcClient;
        this.conversionService = conversionService;
        this.conversionOps = new ConversionOps(conversionService);
        this.delegate = jdbcClient.sql(sql);
        this.polymorphicFieldSpec = new PolymorphicFieldSpec(delegate, conversionService);
    }
    public ConversionAwareStatementSpec withMasterDetailRef(String detailProperty, String masterRef) {
        this.masterDetailRefMap.put(singularize(detailProperty), masterRef);
        return this;
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
    public @NonNull ConversionAwareStatementSpec param(@NonNull String name, int type, Object value) {
        return param(name, new SqlParameterValue(type, value));
    }
    @Override
    public @NonNull ConversionAwareStatementSpec param(@NonNull String name, Object value) {
        value = conversionOps.checkAndConvert(value);
        delegate.param(name, value);
        params.put(name, value);
        return this;
    }

    public <R> PolymorphicFieldSpec.PolymorphicSpec<R> columnDiscriminator(String discriminatorColumn) {
        return polymorphicFieldSpec.new PolymorphicSpec<>(discriminatorColumn);
    }
    public <R> PolymorphicFieldSpec.PolymorphicFieldSpecQueryBuilder<R> columnDiscriminator(String field, String discriminatorColumn) {
        return polymorphicFieldSpec.new PolymorphicFieldSpecQueryBuilder<>(field, discriminatorColumn, polymorphicFieldSpec::addField);
    }
    public <T> @NonNull JdbcClient.MappedQuerySpec<T> query(@NonNull Class<T> resultType) {
        if (ConversionOps.isScalarType(resultType)) {
            var singleColumnMapper = SingleColumnRowMapper.newInstance(resultType);
            return delegate.query(singleColumnMapper);
        } else {
            var nestedRowMapperStream = buildMappers(resultType, Path.empty(), null)
                    .toList();
            var extractor = new MultiLevelExtractor<T>(masterDetailRefMap, nestedRowMapperStream);
            Collection<T> data = delegate.query(extractor);
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
    private static Collection<PropertyDescriptor> findCollectionProperties(Class<?> type) {
        if (type.isRecord()) {
            // for records, inspect the record components, not bean descriptors
            return Arrays.stream(type.getRecordComponents())
                    .filter(rc -> Collection.class.isAssignableFrom(rc.getType()))
                    .map(rc -> {
                        try {
                            return new PropertyDescriptor(rc.getName(), type, rc.getName(),null);
                        } catch (IntrospectionException e) {
                            throw new IllegalStateException(
                                    "Cannot create property descriptor for record component " + rc.getName(), e);
                        }
                    }).toList();
        }
        return Arrays.stream(BeanUtils.getPropertyDescriptors(type))
                .filter(pd ->
                        Collection.class.isAssignableFrom(pd.getPropertyType()) &&
                        pd.getReadMethod()  != null &&
                        pd.getWriteMethod() != null
                ).toList();
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
    private static String singularize(String s) {
        return s.endsWith("s") && s.length()>1
                ? s.substring(0, s.length()-1)
                : s;
    }
    private <U> Stream<RowMapperRef> buildMappers(Class<U> type, Path path, PropertyDescriptor propertyDescriptor) {
        String prefix = path.to();
        NestedRowMapper<U> mapper = NestedRowMapper
                .newInstance(type, conversionService)
                .withPrefix(prefix);
        Collection<PropertyDescriptor> cols = findCollectionProperties(type);

        var children = cols.stream().flatMap(pd -> {
            String singularized = singularize(pd.getName());
            String newPrefix = prefix.isEmpty() ? singularized : prefix+ "_" + singularized;
            return buildMappers(extractGenericType(pd.getReadMethod()), new Path(prefix, newPrefix), pd);
        });
        return Stream.concat(Stream.of(new RowMapperRef(path, type, propertyDescriptor, mapper)), children);
    }
}
