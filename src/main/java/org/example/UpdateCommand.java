package org.example;

import java.util.function.Predicate;
import org.example.model.QueryResult;
import org.example.model.Row;
import org.example.model.TableSchema;
import org.example.model.Column;
import org.example.storage.Database;
import java.util.HashMap;
import java.util.Map;

/**
 * Команда UPDATE для изменения значений в существующих записях таблицы.
 * Изменяет указанный столбец во всех строках, подходящих под условие WHERE.
 */
public class UpdateCommand implements QueryCommand {

    private String tableName;
    private String columnName;
    private String newValue;
    private Predicate<Row> condition;

    /**
     * Создаёт команду UPDATE.
     *
     * @param tableName имя таблицы
     * @param columnName имя изменяемого столбца
     * @param newValue новое значение
     * @param condition условие, определяющее изменяемые строки
     */
    public UpdateCommand(String tableName, String columnName, String newValue, Predicate<Row> condition) {
        if (tableName == null || tableName.isBlank()) {
            throw new IllegalArgumentException("Не указано имя таблицы");
        }
        if (columnName == null || columnName.isBlank()) {
            throw new IllegalArgumentException("Не указан столбец");
        }
        if (newValue == null) {
            throw new IllegalArgumentException("Не указано новое значение");
        }
        if (condition == null) {
            throw new IllegalArgumentException("Не указано условие WHERE");
        }
        this.tableName = tableName;
        this.columnName = columnName;
        this.newValue = newValue;
        this.condition = condition;
    }

    /**
     * Выполняет обновление записей в таблице.
     * Преобразует новое значение в соответствии с типом столбца
     * и передаёт его базе данных для обновления подходящих строк.
     *
     * @param db база данных, в которой выполняется обновление
     * @return результат операции с количеством изменённых строк
     * @throws Exception если при обновлении произошла ошибка
     */
    @Override
    public QueryResult execute(Database db) throws Exception {
        long start = System.currentTimeMillis();
        TableSchema schema = db.getTable(tableName).getSchema();
        if (!schema.hasColumn(columnName)) {
            throw new IllegalArgumentException("Столбец не найден: " + columnName);
        }
        Column column = schema.getColumn(columnName);
        Object parsedValue = column.getType().parse(newValue);
        Map<String, Object> newValues = new HashMap<>();
        newValues.put(columnName, parsedValue);
        int affectedRows = db.update(tableName, condition, newValues);
        long executionTimeMs = System.currentTimeMillis() - start;
        return QueryResult.forMutation(affectedRows, executionTimeMs);
    }

}