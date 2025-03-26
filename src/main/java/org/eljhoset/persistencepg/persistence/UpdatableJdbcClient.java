package org.eljhoset.persistencepg.persistence;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.util.Map;
import java.util.function.Function;

interface UpdatableJdbcClient extends JdbcClient {
    UpdateSpec update(String tableName);
    BatchUpdateSpec.WhereStep batchUpdate(String tableName);

    InsertSpec insert(String tableName);

    interface UpdateSpec {
        UpdateSpec param(String name, Object value);
        UpdateSpec setNull(String name);
        default <T> UpdateSpec param(T value, Function<T, Map<String, Object>>params){
            return param(params.apply(value));
        }
        default UpdateSpec param(Map<String, Object> params){
            params.forEach(this::param);
            return this;
        }
        ExecuteStep where(String where, Map<String, Object> whereColumns);
        interface ExecuteStep {
            int execute();
        }
    }

    interface BatchUpdateSpec {

        interface WhereStep {
            ParamStep where(String where, String ... idColumns);
        }
        interface ParamStep {
            ParamStep param(String name, Object value);
            ParamStep setNull(String name);
            ParamStep also();
            int[] execute();
        }
    }

    interface InsertSpec {
        InsertSpec param(String name, Object value);
        default <T> InsertSpec param(T value, Function<T, Map<String, Object>>params){
            return param(params.apply(value));
        }
        default InsertSpec param(Map<String, Object> params){
            params.forEach(this::param);
            return this;
        }
        int execute(KeyHolder keyHolder, String... generateColumns);
        default KeyHolderMapper execute(String... generateColumns) {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            execute(keyHolder, generateColumns);
            return () -> keyHolder;
        }
        @FunctionalInterface
        interface KeyHolderMapper {
            KeyHolder keyHolder();
            default <R> R map(Function<KeyHolder, R> keyHolderMapper){
                return keyHolderMapper.apply(keyHolder());
            }
        }
    }
}
