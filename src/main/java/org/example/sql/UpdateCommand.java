package org.example.sql;

import org.example.exception.DatabaseException;
import org.example.model.Column;
import org.example.model.QueryResult;
import org.example.model.TableSchema;
import org.example.model.WhereCondition;
import org.example.storage.Database;

import java.util.HashMap;
import java.util.Map;

/**
 * Команда UPDATE: меняет одну колонку во всех строках, подходящих под WHERE.
 */
public class UpdateCommand implements QueryCommand {
    private final String tableName;
    private final String columnName;
    private final String newValue;
    private final WhereCondition condition;

    /**
     * @param tableName  имя таблицы
     * @param columnName колонка, которую меняем
     * @param newValue   новое значение в том виде, как оно написано в запросе
     * @param condition  условие WHERE
     */
    public UpdateCommand(String tableName, String columnName, String newValue, WhereCondition condition) {
        this.tableName = tableName;
        this.columnName = columnName;
        this.newValue = newValue;
        this.condition = condition;
    }

    @Override
    public QueryResult execute(Database db) throws DatabaseException {
        long start = System.currentTimeMillis();
        TableSchema schema = db.getTable(tableName).getSchema();
        Column column = schema.getColumn(columnName);
        if (column == null) {
            throw new DatabaseException("В таблице '" + tableName + "' нет колонки '" + columnName + "'");
        }
        Object parsedValue;
        try {
            parsedValue = column.getType().parse(newValue);
        } catch (IllegalArgumentException e) {
            throw new DatabaseException(e.getMessage());
        }
        Map<String, Object> updates = new HashMap<>();
        updates.put(column.getName(), parsedValue);
        int affectedRows = db.update(tableName, condition, updates);
        long executionTimeMs = System.currentTimeMillis() - start;
        return QueryResult.forMutation(affectedRows, executionTimeMs);
    }
}
