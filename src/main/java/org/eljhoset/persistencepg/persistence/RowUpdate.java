package org.eljhoset.persistencepg.persistence;

import java.util.Collection;
import java.util.Map;

record RowUpdate(Map<String, Object> params) {
    Collection<String> columns() {
        return params.keySet();
    }
}
