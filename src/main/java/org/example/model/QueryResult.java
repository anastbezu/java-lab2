package org.example.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Результат выполнения запроса: передаётся из движка запросов в консоль.
 * Для SELECT содержит колонки и строки, для остальных команд — сообщение.
 */
public class QueryResult {
    private final String message;
    private final List<String> columns;
    private final List<Row> rows;
    private final int affectedRows;
    private final long executionTimeMs;

    private QueryResult(String message, List<String> columns, List<Row> rows,
                        int affectedRows, long executionTimeMs) {
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
        return new QueryResult("Найдено строк: " + rows.size(), columns, rows, rows.size(), executionTimeMs);
    }

    /**
     * Результат INSERT, UPDATE или DELETE.
     */
    public static QueryResult forMutation(int affectedRows, long executionTimeMs) {
        return new QueryResult("Задействовано строк: " + affectedRows,
            new ArrayList<>(), new ArrayList<>(), affectedRows, executionTimeMs);
    }

    /**
     * Результат CREATE TABLE, DROP TABLE, CREATE INDEX.
     */
    public static QueryResult forDdl(String message, long executionTimeMs) {
        return new QueryResult(message, new ArrayList<>(), new ArrayList<>(), 0, executionTimeMs);
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
