package org.example.sql;

import org.example.exception.DatabaseException;
import org.example.model.QueryResult;
import org.example.model.WhereCondition;
import org.example.storage.Database;

/**
 * Команда DELETE: удаляет строки, подходящие под WHERE.
 */
public class DeleteCommand implements QueryCommand {
    private final String tableName;
    private final WhereCondition condition;

    /**
     * @param tableName имя таблицы
     * @param condition условие WHERE
     */
    public DeleteCommand(String tableName, WhereCondition condition) {
        this.tableName = tableName;
        this.condition = condition;
    }

    @Override
    public QueryResult execute(Database db) throws DatabaseException {
        long start = System.currentTimeMillis();
        int affectedRows = db.delete(tableName, condition);
        long executionTimeMs = System.currentTimeMillis() - start;
        return QueryResult.forMutation(affectedRows, executionTimeMs);
    }
}
