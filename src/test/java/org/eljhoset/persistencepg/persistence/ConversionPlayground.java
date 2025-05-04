package org.eljhoset.persistencepg.persistence;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapperImpl;

import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ConversionPlayground {
    public static void main(String[] args) {
        record Detail(BigDecimal quantity, BigDecimal amount) { }
        record Master(Integer id, Collection<Detail> details) { }

        List<Map<String, Object>> rows = List.of(
                Map.of("id", 1, "detail_amount", new BigDecimal(100), "detail_quantity", new BigDecimal(1)),
                Map.of("id", 1, "detail_amount", new BigDecimal(200), "detail_quantity", new BigDecimal(2)),
                Map.of("id", 2, "detail_amount", new BigDecimal(300), "detail_quantity", new BigDecimal(3))
        );

        record Mapper<T>(String prefix, Class<T> mappedClass) implements Function<Map<String, Object>, T> {
            @Override
            public T apply(Map<String, Object> rows) {
                Constructor<T> constructor = BeanUtils.getResolvableConstructor(mappedClass);
                String[] parameterNames = BeanUtils.getParameterNames(constructor);
                int paramCount = constructor.getParameterCount();
                Object[] args = new Object[paramCount];
                for (int i = 0; i < paramCount; i++) {
                    String parameterName = parameterNames[i];
                    var base = prefix.isEmpty() ? prefix : prefix + "_";
                    args[i] = rows.get(base + parameterName);
                }
                return BeanUtils.instantiateClass(constructor, args);
            }
        }

        UnaryOperator<String> singularize = s -> s.endsWith("s") && s.length() > 1
                ? s.substring(0, s.length() - 1)
                : s;

        Function<Method, ? extends Class<?>> extractGenericType = getter -> {
            Type rt = getter.getGenericReturnType();
            if (rt instanceof ParameterizedType p) {
                Type arg = p.getActualTypeArguments()[0];
                if (arg instanceof Class<?>) {
                    return (Class<?>) arg;
                }
            }
            throw new IllegalStateException("Cannot resolve generic type of " + getter);
        };
        record Path(String from, String to) { }
        record MapperRef(Path path, Mapper<?> mapper, PropertyDescriptor propertyDescriptor) { }
        Function<Class<?>, Collection<MapperRef>> getMappers = type -> {
            Mapper<?> mapper = new Mapper<>("", type);
            var children = Arrays.stream(type.getRecordComponents())
                    .filter(rc -> Collection.class.isAssignableFrom(rc.getType()))
                    .map(rc -> {
                        try {
                            PropertyDescriptor pd = new PropertyDescriptor(rc.getName(), type, rc.getName(), null);
                            String prefix = singularize.apply(rc.getName());
                            Mapper<?> child = new Mapper<>(prefix, extractGenericType.apply(pd.getReadMethod()));
                            return new MapperRef(new Path("", prefix), child, pd);
                        } catch (IntrospectionException e) {
                            throw new RuntimeException(e);
                        }
                    });
            return Stream.concat(Stream.of(new MapperRef(new Path("", ""), mapper, null)), children)
                    .toList();
        };

        Collection<MapperRef> mappers = getMappers.apply(Master.class);
        record MappedRow(Path path, Map<String, Object> row, Object mapped, PropertyDescriptor propertyDescriptor) { }
        Map<String, String> masterDetailRefMap = Map.of("detail", "id");
        record Id(String prefix, Object value) { }

        List<MappedRow> mapped = rows.stream()
                .flatMap(row -> mappers.stream().map(mapperRef -> {
                    Path path = mapperRef.path;
                    Mapper<?> mapper = mapperRef.mapper;
                    Object apply = mapper.apply(row);
                    return new MappedRow(path, row, apply, mapperRef.propertyDescriptor);
                })).toList();

        Map<Id, Object> refMap = mapped.stream().flatMap(mappedRow -> {
            Path path = mappedRow.path();
            Object object = mappedRow.mapped();
            Map<String, Object> row = mappedRow.row();
            return masterDetailRefMap.values().stream()
                    .flatMap(value -> {
                        Object ref = row.get(value);
                        return Stream.of(
                                Map.entry(new Id(path.from(), ref), object),
                                Map.entry(new Id(path.to(), ref), object)
                        );
                    });
        }).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (k1, _) -> k1));
        record ObjectRef(Object object, PropertyDescriptor propertyDescriptor) { }
        Map<ObjectRef, Object[]> groupedMappings = mapped.stream().<Map.Entry<ObjectRef, Object>>mapMulti((mappedRow, consumer) -> {
            String prefix = masterDetailRefMap.get(mappedRow.path.to);
            if (prefix != null) {
                Object object = mappedRow.row.get(prefix);
                Object ref = refMap.get(new Id(mappedRow.path.from, object));
                ObjectRef objectRef = new ObjectRef(ref, mappedRow.propertyDescriptor);
                consumer.accept(Map.entry(objectRef, mappedRow.mapped));
            }
        }).collect(Collectors.groupingBy(Map.Entry::getKey, Collectors.mapping(Map.Entry::getValue, Collectors.collectingAndThen(
                Collectors.toList(), List::toArray)
        )));
        var objects = groupedMappings.entrySet().stream().map(entry -> {
            ObjectRef objectRef = entry.getKey();
            PropertyDescriptor pd = objectRef.propertyDescriptor;
            Object original = objectRef.object();
            Class<?> recordType = original.getClass();
            Constructor<?> ctor = BeanUtils.getResolvableConstructor(recordType);
            String[] paramNames = BeanUtils.getParameterNames(ctor);
            Object[] recordArgs = new Object[paramNames.length];
            BeanWrapperImpl reader = new BeanWrapperImpl(original);
            for (int i = 0; i < paramNames.length; i++) {
                String name = paramNames[i];
                if (pd.getName().equals(name)) {
                    recordArgs[i] = Arrays.asList(entry.getValue());
                } else {
                    recordArgs[i] = reader.getPropertyValue(name);
                }
            }
            return BeanUtils.instantiateClass(ctor, recordArgs);
        }).toList();

        System.out.println(groupedMappings);
        System.out.println(objects);
    }
}
