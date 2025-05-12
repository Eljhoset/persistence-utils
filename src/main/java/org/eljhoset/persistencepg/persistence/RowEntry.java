package org.eljhoset.persistencepg.persistence;

import lombok.experimental.Delegate;

import java.util.Map;

/**
 * Wrapper to hold original row index along with its data.
 */
public record RowEntry(int rowNumber, @Delegate Map<String, Object> row) {
    public static RowEntry of(int rowNumber, Map<String, Object> values) {
        return new RowEntry(rowNumber, values);
    }
}
