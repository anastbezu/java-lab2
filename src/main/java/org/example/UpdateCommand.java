package org.example;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;
import org.example.exception.DatabaseException;
import org.example.model.Column;
import org.example.model.DataType;
import org.example.model.QueryResult;
import org.example.model.Row;
import org.example.model.TableSchema;
import org.example.storage.Database;

/**
 * Команда UPDATE для изменения значений в существующих записях таблицы.
 * Изменяет указанный столбец во всех строках, подходящих под условие WHERE.
 */
public class UpdateCommand implements QueryCommand {
    private final String tableName;
    private final String columnName;
    private final String newValue;
    private final Predicate<Row> condition;
    private final String conditionColumn;
    private final String conditionOperator;
    /**
     * Создаёт команду UPDATE.
     *
     * @param tableName имя таблицы
     * @param columnName имя изменяемого столбца
     * @param newValue новое значение
     * @param condition условие, определяющее изменяемые строки
     * @param conditionColumn имя столбца из условия WHERE
     * @param conditionOperator оператор условия WHERE
     */
    public UpdateCommand(String tableName, String columnName, String newValue, Predicate<Row> condition, String conditionColumn, String conditionOperator) {
        if (tableName == null || tableName.isBlank()) {
            throw new SqlException("Не указано имя таблицы");
        }
        if (columnName == null || columnName.isBlank()) {
            throw new SqlException("Не указан столбец");
        }
        if (newValue == null) {
            throw new SqlException("Не указано новое значение");
        }
        if (condition == null) {
            throw new SqlException("Не указано условие WHERE");
        }
        if (conditionColumn == null || conditionColumn.isBlank()) {
            throw new SqlException("Не указан столбец в WHERE");
        }
        if (conditionOperator == null || conditionOperator.isBlank()) {
            throw new SqlException("Не указан оператор в WHERE");
        }
        this.tableName = tableName;
        this.columnName = columnName;
        this.newValue = newValue;
        this.condition = condition;
        this.conditionColumn = conditionColumn;
        this.conditionOperator = conditionOperator;
    }
    /**
     * Выполняет обновление записей в таблице.
     * Преобразует новое значение в соответствии с типом столбца
     * и передаёт его базе данных для обновления подходящих строк.
     *
     * @param db база данных, в которой выполняется обновление
     * @return результат операции с количеством изменённых строк
     * @throws DatabaseException если при обращении к базе данных произошла ошибка
     */
    @Override
    public QueryResult execute(Database db) throws DatabaseException {
        if (db == null) {
            throw new SqlException("База данных не указана");
        }
        long start = System.currentTimeMillis();
        TableSchema schema = db.getTable(tableName).getSchema();
        if (!schema.hasColumn(conditionColumn)) {
            throw new SqlException("Столбец в WHERE не найден: " + conditionColumn);
        }
        if (!schema.hasColumn(columnName)) {
            throw new SqlException("Столбец не найден: " + columnName);
        }
        Column conditionField = schema.getColumn(conditionColumn);
        if ((conditionOperator.equals(">") || conditionOperator.equals("<")) && conditionField.getType() != DataType.INT && conditionField.getType() != DataType.FLOAT) {
            throw new SqlException("Операторы > и < можно использовать только с числовыми столбцами");
        }
        Column updateField = schema.getColumn(columnName);
        Object parsedValue = updateField.getType().parse(newValue);
        Map<String, Object> updates = new HashMap<>();
        updates.put(columnName, parsedValue);
        int affectedRows = db.update(tableName, condition, updates);
        long executionTimeMs = System.currentTimeMillis() - start;
        return QueryResult.forMutation(affectedRows, executionTimeMs);
    }
}