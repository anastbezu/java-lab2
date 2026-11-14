package org.example.sql;

import org.example.exception.DatabaseException;
import org.example.model.QueryResult;
import org.example.model.TableSchema;
import org.example.storage.Database;

/**
 * Команда CREATE TABLE.
 */
public class CreateTableCommand implements QueryCommand {
    private final TableSchema schema;

    /**
     * @param schema схема новой таблицы
     */
    public CreateTableCommand(TableSchema schema) {
        this.schema = schema;
    }

    @Override
    public QueryResult execute(Database db) throws DatabaseException {
        long start = System.currentTimeMillis();
        db.createTable(schema);
        long executionTimeMs = System.currentTimeMillis() - start;
        String message = "Таблица " + schema.getTableName() + " создана";
        return QueryResult.forDdl(message, executionTimeMs);
    }
}
