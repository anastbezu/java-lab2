package org.example;

import java.util.function.Predicate;
import org.example.model.QueryResult;
import org.example.model.Row;
import org.example.storage.Database;

/**
 * Команда DELETE для удаления записей из таблицы.
 * Удаляет все строки, соответствующие условию WHERE.
 */
public class DeleteCommand implements QueryCommand {

    private String tableName;
    private Predicate<Row> condition;

    /**
     * Создаёт команду DELETE.
     *
     * @param tableName имя таблицы
     * @param condition условие, определяющее удаляемые строки
     */
    public DeleteCommand(String tableName, Predicate<Row> condition) {
        if (tableName == null || tableName.isBlank()) {
            throw new IllegalArgumentException("Не указано имя таблицы");
        }
        if (condition == null) {
            throw new IllegalArgumentException("Не указано условие WHERE");
        }
        this.tableName = tableName;
        this.condition = condition;
    }

    /**
     * Выполняет удаление записей из таблицы.
     *
     * @param db база данных, из которой удаляются записи
     * @return результат операции с количеством удалённых строк
     * @throws Exception если при удалении произошла ошибка
     */
    @Override
    public QueryResult execute(Database db) throws Exception {
        long start = System.currentTimeMillis();
        int affectedRows = db.delete(tableName, condition);
        long executionTimeMs = System.currentTimeMillis() - start;
        return QueryResult.forMutation(affectedRows, executionTimeMs);
    }

}