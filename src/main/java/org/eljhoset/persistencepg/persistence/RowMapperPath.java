package org.eljhoset.persistencepg.persistence;

public record RowMapperPath(String from, String to) {
    static RowMapperPath empty() {
        return new RowMapperPath("", "");
    }
}
