package org.eljhoset.persistencepg.persistence;

import java.util.Map;

public record PolymorphicField<T>(String field, String discriminatorColumn,
                                  Map<String, Map<Object, Class<? extends T>>> fieldMapping) { }
