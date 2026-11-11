package org.example.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Результат выполнения запроса: передаётся из движка запросов в консоль.
 * Для SELECT содержит колонки и строки, для остальных команд — сообщение.
 */
public class QueryResult {
    private final boolean success;
    private final String message;
    private final List<String> columns;
    private final List<Row> rows;
    private final int affectedRows;
    private final long executionTimeMs;

    private QueryResult(boolean success, String message, List<String> columns, List<Row> rows,
                        int affectedRows, long executionTimeMs) {
        this.success = success;
        this.message = message;
        this.columns = columns;
        this.rows = rows;
        this.affectedRows = affectedRows;
        this.executionTimeMs = executionTimeMs;
    }

    /**
     * Результат SELECT.
     */
    public static QueryResult forSelect(List<String> columns, List<Row> rows, long executionTimeMs) {
        return new QueryResult(true, rows.size() + " rows in set", columns, rows, rows.size(), executionTimeMs);
    }

    /**
     * Результат INSERT, UPDATE или DELETE.
     */
    public static QueryResult forMutation(int affectedRows, long executionTimeMs) {
        return new QueryResult(true, "Query OK, " + affectedRows + " rows affected",
                new ArrayList<>(), new ArrayList<>(), affectedRows, executionTimeMs);
    }

    /**
     * Результат CREATE TABLE, DROP TABLE, CREATE INDEX.
     */
    public static QueryResult forDdl(String message, long executionTimeMs) {
        return new QueryResult(true, message, new ArrayList<>(), new ArrayList<>(), 0, executionTimeMs);
    }

    /**
     * Результат с ошибкой.
     */
    public static QueryResult forError(String errorMessage) {
        return new QueryResult(false, errorMessage, new ArrayList<>(), new ArrayList<>(), 0, 0);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public List<String> getColumns() {
        return columns;
    }

    public List<Row> getRows() {
        return rows;
    }

    public int getAffectedRows() {
        return affectedRows;
    }

    public long getExecutionTimeMs() {
        return executionTimeMs;
    }
}
