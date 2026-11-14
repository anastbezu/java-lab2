package org.example;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.example.exception.DatabaseException;
import org.example.model.QueryResult;
import org.example.model.Row;
import org.example.model.TableSchema;
import org.example.storage.Database;
/**
 * Команда INSERT для добавления одной или нескольких записей в таблицу.
 * Поддерживает вставку значений как по порядку столбцов, так и по их именам.
 */
public class InsertCommand implements QueryCommand {
    private final String tableName;
    private final List<String[]> rowsValues;
    private final String[] columns;
    /**
     * Создаёт команду INSERT для добавления одной записи.
     *
     * @param tableName имя таблицы
     * @param values значения добавляемой записи
     * @param columns имена столбцов или null, если значения указаны по порядку
     */
    public InsertCommand(String tableName, String[] values, String[] columns) {
        this(tableName, Collections.singletonList(values), columns);
    }
    /**
     * Создаёт команду INSERT для добавления нескольких записей.
     *
     * @param tableName имя таблицы
     * @param rowsValues значения добавляемых записей
     * @param columns имена столбцов или null, если значения указаны по порядку
     */
    public InsertCommand(String tableName, List<String[]> rowsValues, String[] columns) {
        if (tableName == null || tableName.isBlank()) {
            throw new SqlException("Не указано имя таблицы");
        }
        if (rowsValues == null || rowsValues.isEmpty()) {
            throw new SqlException("Не указаны значения");
        }
        this.tableName = tableName;
        this.rowsValues = new ArrayList<>();
        for (String[] values : rowsValues) {
            if (values == null || values.length == 0) {
                throw new SqlException("Не указаны значения");
            }
            this.rowsValues.add(values.clone());
        }
        if (columns != null && columns.length == 0) {
            throw new SqlException("Не указаны столбцы");
        }
        this.columns = columns == null ? null : columns.clone();
    }
    /**
     * Выполняет добавление записей в таблицу.
     *
     * @param db база данных, в которую добавляются записи
     * @return результат операции с количеством добавленных строк
     * @throws DatabaseException если при обращении к базе данных произошла ошибка
     */
    @Override
    public QueryResult execute(Database db) throws DatabaseException {
        if (db == null) {
            throw new SqlException("База данных не указана");
        }
        long start = System.currentTimeMillis();
        TableSchema schema = db.getTable(tableName).getSchema();
        List<Row> rows = new ArrayList<>();
        for (String[] values : rowsValues) {
            Row row;
            if (columns == null) {
                row = schema.createRowFromPositionalValues(Arrays.asList(values));
            } else {
                row = schema.createRowFromNamedValues(Arrays.asList(columns), Arrays.asList(values));
            }
            rows.add(row);
        }
        int affectedRows = db.insertAll(tableName, rows);
        long executionTimeMs = System.currentTimeMillis() - start;
        return QueryResult.forMutation(affectedRows, executionTimeMs);
    }
}