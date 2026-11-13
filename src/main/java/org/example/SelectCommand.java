package org.example;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import org.example.exception.DatabaseException;
import org.example.model.Column;
import org.example.model.DataType;
import org.example.model.QueryResult;
import org.example.model.Row;
import org.example.storage.Database;
/**
 * Команда SELECT для получения данных из таблицы.
 * Поддерживает выбор всех столбцов или отдельных столбцов,
 * а также фильтрацию строк с помощью условия WHERE.
 */
public class SelectCommand implements QueryCommand {
    private final String tableName;
    private final String[] columns;
    private final Predicate<Row> condition;
    private final String whereClause;
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
            throw new SqlException("Не указано имя таблицы");
        }
        if (columns == null || columns.length == 0) {
            throw new SqlException("Не указаны столбцы");
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
     * @throws DatabaseException если при обращении к базе данных произошла ошибка
     */
    @Override
    public QueryResult execute(Database db) throws DatabaseException {
        if (db == null) {
            throw new SqlException("База данных не указана");
        }
        long start = System.currentTimeMillis();
        List<Row> rows;
        if (whereClause != null) {
            String[] conditionParts = SqlParser.parseConditionParts(whereClause);
            Column conditionColumn = db.getTable(tableName).getSchema().getColumn(conditionParts[0]);
            if (conditionColumn == null) {
                throw new SqlException("Столбец в WHERE не найден: " + conditionParts[0]);
            }
            if ((conditionParts[1].equals(">") || conditionParts[1].equals("<")) && conditionColumn.getType() != DataType.INT && conditionColumn.getType() != DataType.FLOAT) {
                throw new SqlException("Операторы > и < можно использовать только с числовыми столбцами");
            }
            if (conditionParts[1].equals("=")) {
                rows = db.selectEquals(tableName, conditionParts[0], conditionParts[2], Arrays.asList(columns));
            } else {
                rows = db.select(tableName, Arrays.asList(columns), condition);
            }
        } else {
            rows = db.select(tableName, Arrays.asList(columns), condition);
        }
        long executionTimeMs = System.currentTimeMillis() - start;
        return QueryResult.forSelect(Arrays.asList(columns), rows, executionTimeMs);
    }
}