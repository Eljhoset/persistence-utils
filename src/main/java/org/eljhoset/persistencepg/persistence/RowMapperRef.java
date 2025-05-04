package org.eljhoset.persistencepg.persistence;

import java.beans.PropertyDescriptor;

record RowMapperRef(Path path, Class<?> type, PropertyDescriptor propertyDescriptor, NestedRowMapper<?> rowMapper) { }
