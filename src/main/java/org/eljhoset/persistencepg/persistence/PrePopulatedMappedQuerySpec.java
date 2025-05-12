package org.eljhoset.persistencepg.persistence;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.lang.NonNull;

import java.util.List;
import java.util.stream.Stream;

public record PrePopulatedMappedQuerySpec<T>(List<T> data) implements JdbcClient.MappedQuerySpec<T> {
    @Override
    public @NonNull Stream<T> stream() {
        return data.stream();
    }

    @Override
    public @NonNull List<T> list() {
        return data;
    }
}
