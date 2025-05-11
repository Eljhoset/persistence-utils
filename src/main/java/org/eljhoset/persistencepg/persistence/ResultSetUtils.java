package org.eljhoset.persistencepg.persistence;

import lombok.experimental.UtilityClass;
import org.springframework.jdbc.support.JdbcUtils;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

@UtilityClass
public class ResultSetUtils {
    public static Map<String, Object> extractColumnValues(ResultSet rs) throws SQLException {
        Map<String, Object> mapOfColumnValues = new HashMap<>();
        ResultSetMetaData rsmd = rs.getMetaData();
        int columnCount = rsmd.getColumnCount();
        for (int i = 1; i <= columnCount; i++) {
            String column = JdbcUtils.lookupColumnName(rsmd, i);
            Object value = JdbcUtils.getResultSetValue(rs, i);
            mapOfColumnValues.put(column, value);
        }
        return mapOfColumnValues;
    }
}
