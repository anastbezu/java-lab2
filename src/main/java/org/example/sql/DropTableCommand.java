package org.example.sql;

import org.example.exception.DatabaseException;
import org.example.model.QueryResult;
import org.example.storage.Database;

/**
 * Команда DROP TABLE.
 */
public class DropTableCommand implements QueryCommand {
    private final String tableName;

    /**
     * @param tableName имя удаляемой таблицы
     */
    public DropTableCommand(String tableName) {
        this.tableName = tableName;
    }

    @Override
    public QueryResult execute(Database db) throws DatabaseException {
        long start = System.currentTimeMillis();
        db.dropTable(tableName);
        long executionTimeMs = System.currentTimeMillis() - start;
        String message = "Таблица " + tableName + " удалена";
        return QueryResult.forDdl(message, executionTimeMs);
    }
}
