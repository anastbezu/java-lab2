package org.example;

import org.example.model.QueryResult;
import org.example.model.Row;
import org.example.model.TableSchema;
import org.example.storage.Database;
import java.util.Arrays;

/**
 * Команда INSERT для добавления новой записи в таблицу.
 * Поддерживает добавление значений в порядке столбцов
 * или с указанием названий столбцов.
 */
public class InsertCommand implements QueryCommand {

    private String tableName;
    private String[] values;
    private String[] columns;

    /**
     * Создаёт команду INSERT.
     *
     * @param tableName имя таблицы
     * @param values значения для добавления
     * @param columns названия столбцов или null, если указан порядок всех столбцов
     */
    public InsertCommand(String tableName, String[] values, String[] columns) {
        if (tableName == null || tableName.isBlank()) {
            throw new IllegalArgumentException("Не указано имя таблицы");
        }
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("Не указаны значения");
        }
        this.tableName = tableName;
        this.values = values.clone();
        this.columns = columns == null ? null : columns.clone();
    }

    /**
     * Выполняет добавление записи в таблицу.
     * Преобразует переданные значения в соответствии со схемой таблицы.
     *
     * @param db база данных, в которую добавляется запись
     * @return результат выполнения операции
     * @throws Exception если произошла ошибка при добавлении записи
     */
    @Override
    public QueryResult execute(Database db) throws Exception {
        long start = System.currentTimeMillis();
        TableSchema schema = db.getTable(tableName).getSchema();
        Row row;
        if (columns == null) {
            row = schema.createRowFromPositionalValues(
                    Arrays.asList(values)
            );
        } else {
            row = schema.createRowFromNamedValues(
                    Arrays.asList(columns),
                    Arrays.asList(values)
            );
        }
        db.insertInto(tableName, row);

        long executionTimeMs = System.currentTimeMillis() - start;

        return QueryResult.forMutation(1, executionTimeMs);
    }

}