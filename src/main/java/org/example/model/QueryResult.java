package org.example.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Результат запроса, который команда отдаёт в консоль.
 * У SELECT есть колонки и строки, у остальных команд только сообщение.
 */
public class QueryResult {
    private final String message;
    private final List<String> columns;
    private final List<Row> rows;
    private final long executionTimeMs;

    private QueryResult(String message, List<String> columns, List<Row> rows, long executionTimeMs) {
        this.message = message;
        this.columns = columns;
        this.rows = rows;
        this.executionTimeMs = executionTimeMs;
    }

    /**
     * Результат SELECT.
     */
    public static QueryResult forSelect(List<String> columns, List<Row> rows, long executionTimeMs) {
        String message = "Найдено строк: " + rows.size();
        return new QueryResult(message, columns, rows, executionTimeMs);
    }

    /**
     * Результат INSERT, UPDATE или DELETE.
     */
    public static QueryResult forMutation(int affectedRows, long executionTimeMs) {
        String message = "Задействовано строк: " + affectedRows;
        return new QueryResult(message, new ArrayList<>(), new ArrayList<>(), executionTimeMs);
    }

    /**
     * Результат CREATE TABLE, DROP TABLE или CREATE INDEX.
     */
    public static QueryResult forDdl(String message, long executionTimeMs) {
        return new QueryResult(message, new ArrayList<>(), new ArrayList<>(), executionTimeMs);
    }

    /**
     * Возвращает сообщение для пользователя.
     */
    public String getMessage() {
        return message;
    }

    /**
     * Возвращает колонки результата. Для команд кроме SELECT список пустой.
     */
    public List<String> getColumns() {
        return columns;
    }

    /**
     * Возвращает найденные строки. Для команд кроме SELECT список пустой.
     */
    public List<Row> getRows() {
        return rows;
    }

    /**
     * Возвращает, сколько миллисекунд выполнялся запрос.
     */
    public long getExecutionTimeMs() {
        return executionTimeMs;
    }
}
