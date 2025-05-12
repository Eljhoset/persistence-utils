package org.eljhoset.persistencepg.persistence;

import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.PropertyAccessorFactory;
import org.springframework.core.convert.ConversionService;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.SingleColumnRowMapper;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.lang.NonNull;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.function.LongSupplier;

public class ConversionAwareStatementSpec implements JdbcClient.StatementSpec {
    private final String sql;
    private final JdbcClient jdbcClient;
    private final @Delegate JdbcClient.StatementSpec delegate;
    private final ConversionService conversionService;
    private final ConversionOps conversionOps;
    private final PolymorphicFieldSpec polymorphicFieldSpec;
    private final Map<String, Object> params = new HashMap<>();
    private final Map<String, String> groupingRules = new HashMap<>();

    public ConversionAwareStatementSpec(String sql, JdbcClient jdbcClient, ConversionService conversionService) {
        this.sql = sql;
        this.jdbcClient = jdbcClient;
        this.conversionService = conversionService;
        this.conversionOps = new ConversionOps(conversionService);
        this.delegate = jdbcClient.sql(sql);
        this.polymorphicFieldSpec = new PolymorphicFieldSpec(delegate, conversionService);
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
            var extractor = new MapperExtractor<>(resultType, conversionService, groupingRules);
            final List<T> data = delegate.query(extractor);
            return new PrePopulatedMappedQuerySpec<>(data);
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

    public GroupingBuilder group(String property) {
        return new GroupingBuilder(property, this);
    }

    @RequiredArgsConstructor
    public class GroupingBuilder {
        private final String property;
        private final ConversionAwareStatementSpec statementSpec;

        public ConversionAwareStatementSpec by(String field) {
            groupingRules.put(property, field);
            return statementSpec;
        }

    }

    private record MapperExtractor<T>(Class<T> mappedClass, ConversionService conversionService, Map<String, String> groupingRules) implements ResultSetExtractor<List<T>> {
        @Override
        public List<T> extractData(@NonNull ResultSet rs) throws DataAccessException, SQLException {
            var rows = new ArrayList<RowEntry>();
            int index = 0;
            while (rs.next()){
                var row = ResultSetUtils.extractColumnValues(index++, rs);
                rows.add(row);
            }
            var delegate = new MapperExtractorDelegate<>(mappedClass, conversionService, rows, Map.of(), groupingRules);
            return delegate.extractData();
        }
    }
}
