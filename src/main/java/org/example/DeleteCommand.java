package org.example;
import java.util.function.Predicate;
import org.example.exception.DatabaseException;
import org.example.model.Column;
import org.example.model.DataType;
import org.example.model.QueryResult;
import org.example.model.Row;
import org.example.model.TableSchema;
import org.example.storage.Database;
/**
 * Команда DELETE для удаления записей из таблицы.
 * Удаляет все строки, соответствующие условию WHERE.
 */
public class DeleteCommand implements QueryCommand {
    private final String tableName;
    private final Predicate<Row> condition;
    private final String conditionColumn;
    private final String conditionOperator;
    /**
     * Создаёт команду DELETE.
     *
     * @param tableName имя таблицы
     * @param condition условие, определяющее удаляемые строки
     * @param conditionColumn имя столбца из условия WHERE
     * @param conditionOperator оператор условия WHERE
     */
    public DeleteCommand(String tableName, Predicate<Row> condition, String conditionColumn, String conditionOperator) {
        if (tableName == null || tableName.isBlank()) {
            throw new SqlException("Не указано имя таблицы");
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
        this.condition = condition;
        this.conditionColumn = conditionColumn;
        this.conditionOperator = conditionOperator;
    }
    /**
     * Выполняет удаление записей из таблицы.
     *
     * @param db база данных, из которой удаляются записи
     * @return результат операции с количеством удалённых строк
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
            throw new SqlException("Столбец в WHERE не найден: " + conditionColumn
            );
        }
        Column conditionField = schema.getColumn(conditionColumn);
        if ((conditionOperator.equals(">") || conditionOperator.equals("<")) && conditionField.getType() != DataType.INT && conditionField.getType() != DataType.FLOAT) {
            throw new SqlException("Операторы > и < можно использовать только с числовыми столбцами");
        }
        int affectedRows = db.delete(tableName, condition);
        long executionTime = System.currentTimeMillis() - start;
        return QueryResult.forMutation(affectedRows, executionTime);
    }
}