package org.eljhoset.persistencepg.persistence;

import lombok.experimental.UtilityClass;

@UtilityClass
class JdbcPaginationUtil {
    /**
     * Extracts the part of the SQL string starting from the first occurrence
     * of "FROM" (case-insensitive). If "FROM" is not found, returns an empty string.
     *
     * @param sql the SQL query string
     * @return the substring starting with "FROM", or an empty string if not found
     */
    static String extractFromClause(String sql) {
        if (sql == null) {
            return "";
        }
        // Find the index of "from" in a case-insensitive manner
        int index = sql.toLowerCase().indexOf("from");
        if (index == -1) {
            return "";
        }
        // Return the substring starting at the found index, trimmed of extra whitespace
        return sql.substring(index).trim();
    }
}
