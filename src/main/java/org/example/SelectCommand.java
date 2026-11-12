package org.example;

import org.example.model.QueryResult;
import org.example.model.Row;
import org.example.storage.Database;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import org.example.model.Column;

/**
 * Команда SELECT для получения данных из таблицы.
 * Поддерживает выбор всех столбцов или отдельных столбцов,
 * а также фильтрацию строк с помощью условия WHERE.
 */
public class SelectCommand implements QueryCommand {

    private String tableName;
    private String[] columns;
    private Predicate<Row> condition;
    private String whereClause;

    /**
     * Создаёт команду SELECT.
     *
     * @param tableName имя таблицы
     * @param columns столбцы, которые нужно получить
     * @param condition условие фильтрации строк
     * @param whereClause исходное условие WHERE
     */
    public SelectCommand(String tableName, String[] columns, Predicate<Row> condition, String whereClause) {
        if (tableName == null || tableName.isBlank()) {
            throw new IllegalArgumentException("Не указано имя таблицы");
        }
        if (columns == null || columns.length == 0) {
            throw new IllegalArgumentException("Не указаны столбцы");
        }
        this.tableName = tableName;
        this.columns = columns.clone();
        this.condition = condition;
        this.whereClause = whereClause;
    }

    /**
     * Выполняет SELECT-запрос к базе данных.
     * Для условия равенства использует метод selectEquals(),
     * который может задействовать индекс.
     *
     * @param db база данных, из которой извлекаются записи
     * @return результат SELECT-запроса
     * @throws Exception если при выполнении запроса произошла ошибка
     */
    @Override
    public QueryResult execute(Database db) throws Exception {
        long start = System.currentTimeMillis();
        List<Row> rows;
        if (whereClause != null) {
            String[] parts = whereClause.trim().split("\\s+");
            if (parts.length == 3 && parts[1].equals("=")) {
                Column column = db.getTable(tableName).getSchema().getColumn(parts[0]);
                if (column == null) {
                    throw new IllegalArgumentException("Столбец не найден: " + parts[0]);
                }
                Object value = column.getType().parse(parts[2]);
                rows = db.selectEquals(tableName, parts[0], value, Arrays.asList(columns));
            }
            else {
                rows = db.select(tableName, Arrays.asList(columns), condition);
            }
        }
        else {
            rows = db.select(tableName, Arrays.asList(columns), condition);
        }
        long executionTimeMs = System.currentTimeMillis() - start;
        return QueryResult.forSelect(Arrays.asList(columns), rows, executionTimeMs);
    }

}