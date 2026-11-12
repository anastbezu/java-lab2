package org.example;

import org.example.model.QueryResult;
import org.example.storage.Database;

/**
 * Команда DROP TABLE для удаления таблицы из базы данных.
 */
public class DropTableCommand implements QueryCommand {

    private String tableName;

    /**
     * Создаёт команду DROP TABLE.
     *
     * @param tableName имя удаляемой таблицы
     */
    public DropTableCommand(String tableName) {
        if (tableName == null || tableName.isBlank()) {
            throw new IllegalArgumentException("Не указано имя таблицы");
        }
        this.tableName = tableName;
    }

    /**
     * Удаляет таблицу из базы данных.
     *
     * @param db база данных, из которой удаляется таблица
     * @return результат выполнения команды
     * @throws Exception если при удалении таблицы произошла ошибка
     */
    @Override
    public QueryResult execute(Database db) throws Exception {
        long start = System.currentTimeMillis();
        db.dropTable(tableName);
        long executionTimeMs = System.currentTimeMillis() - start;
        return QueryResult.forDdl("Таблица " + tableName + " удалена", executionTimeMs);
    }
}