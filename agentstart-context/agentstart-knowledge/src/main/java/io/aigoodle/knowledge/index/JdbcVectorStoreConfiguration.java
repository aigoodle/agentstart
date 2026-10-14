package io.aigoodle.knowledge.index;

import java.util.regex.Pattern;

/** Immutable, validated scope for one JDBC-backed vector store. */
public record JdbcVectorStoreConfiguration(String datasetId, String tableName) {

    private static final Pattern SQL_IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*");

    public JdbcVectorStoreConfiguration {
        if (datasetId == null || datasetId.isBlank()) {
            throw new IllegalArgumentException("datasetId must not be blank");
        }
        requireValidTableName(tableName);
    }

    static String requireValidTableName(String tableName) {
        if (tableName == null || !SQL_IDENTIFIER.matcher(tableName).matches()) {
            throw new IllegalArgumentException("Invalid vector table name: " + tableName);
        }
        return tableName;
    }
}
