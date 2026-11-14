package org.example.sql;

import org.example.exception.DatabaseException;
import org.example.model.QueryResult;
import org.example.storage.Database;

/**
 * Команда CREATE INDEX: строит индекс по колонке, чтобы поиск по ней шёл быстрее.
 */
public class CreateIndexCommand implements QueryCommand {
    private final String indexName;
    private final String tableName;
    private final String columnName;

    /**
     * @param indexName  имя индекса
     * @param tableName  имя таблицы
     * @param columnName колонка, по которой строится индекс
     */
    public CreateIndexCommand(String indexName, String tableName, String columnName) {
        this.indexName = indexName;
        this.tableName = tableName;
        this.columnName = columnName;
    }

    @Override
    public QueryResult execute(Database db) throws DatabaseException {
        long start = System.currentTimeMillis();
        db.createIndex(indexName, tableName, columnName);
        long executionTimeMs = System.currentTimeMillis() - start;
        String message = "Индекс " + indexName + " создан";
        return QueryResult.forDdl(message, executionTimeMs);
    }
}
