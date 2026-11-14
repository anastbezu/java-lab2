package org.example.sql;

import org.example.exception.DatabaseException;
import org.example.model.Column;
import org.example.model.QueryResult;
import org.example.model.Row;
import org.example.model.WhereCondition;
import org.example.storage.Database;

import java.util.ArrayList;
import java.util.List;

/**
 * Команда SELECT: выбирает нужные колонки, при необходимости по условию WHERE.
 */
public class SelectCommand implements QueryCommand {
    private final String tableName;
    private final List<String> columns;
    private final WhereCondition condition;

    /**
     * @param tableName имя таблицы
     * @param columns   колонки или один элемент "*"
     * @param condition условие WHERE или null, если его нет
     */
    public SelectCommand(String tableName, List<String> columns, WhereCondition condition) {
        this.tableName = tableName;
        this.columns = columns;
        this.condition = condition;
    }

    @Override
    public QueryResult execute(Database db) throws DatabaseException {
        long start = System.currentTimeMillis();
        List<String> resultColumns = new ArrayList<>();
        if (columns.size() == 1 && columns.get(0).equals("*")) {
            for (Column column : db.getTable(tableName).getSchema().getColumns()) {
                resultColumns.add(column.getName());
            }
        } else {
            resultColumns.addAll(columns);
        }
        List<Row> rows = db.select(tableName, resultColumns, condition);
        long executionTimeMs = System.currentTimeMillis() - start;
        return QueryResult.forSelect(resultColumns, rows, executionTimeMs);
    }
}
