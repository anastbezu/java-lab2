package org.example.sql;

import org.example.exception.DatabaseException;
import org.example.model.QueryResult;
import org.example.model.Row;
import org.example.model.TableSchema;
import org.example.storage.Database;

import java.util.ArrayList;
import java.util.List;

/**
 * Команда INSERT: добавляет одну или несколько строк.
 * Значения можно передать по порядку колонок или с явным списком колонок.
 */
public class InsertCommand implements QueryCommand {
    private final String tableName;
    private final List<String> columns;
    private final List<List<String>> rowsValues;

    /**
     * @param tableName  имя таблицы
     * @param columns    имена колонок или null, если значения идут по порядку
     * @param rowsValues значения строк в том виде, как они написаны в запросе
     */
    public InsertCommand(String tableName, List<String> columns, List<List<String>> rowsValues) {
        this.tableName = tableName;
        this.columns = columns;
        this.rowsValues = rowsValues;
    }

    @Override
    public QueryResult execute(Database db) throws DatabaseException {
        long start = System.currentTimeMillis();
        TableSchema schema = db.getTable(tableName).getSchema();
        List<Row> rows = new ArrayList<>();
        for (List<String> values : rowsValues) {
            Row row;
            if (columns == null) {
                row = schema.createRowFromPositionalValues(values);
            } else {
                row = schema.createRowFromNamedValues(columns, values);
            }
            rows.add(row);
        }
        int affectedRows = db.insertAll(tableName, rows);
        long executionTimeMs = System.currentTimeMillis() - start;
        return QueryResult.forMutation(affectedRows, executionTimeMs);
    }
}
