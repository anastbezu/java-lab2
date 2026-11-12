package org.example;

import org.example.model.QueryResult;
import org.example.model.TableSchema;
import org.example.storage.Database;

/**
 * Команда CREATE TABLE для создания новой таблицы.
 * Использует схему, содержащую названия и типы столбцов таблицы.
 */
public class CreateTableCommand implements QueryCommand {

    private TableSchema schema;

    /**
     * Создаёт команду CREATE TABLE.
     *
     * @param schema схема создаваемой таблицы
     */
    public CreateTableCommand(TableSchema schema) {
        this.schema = schema;
    }

    /**
     * Создаёт таблицу в базе данных на основе переданной схемы.
     *
     * @param db база данных, в которой создаётся таблица
     * @return результат выполнения команды
     * @throws Exception если при создании таблицы произошла ошибка
     */
    @Override
    public QueryResult execute(Database db) throws Exception {
        long start = System.currentTimeMillis();
        db.createTable(schema);
        long executionTimeMs = System.currentTimeMillis() - start;
        return QueryResult.forDdl(
                "Таблица " + schema.getTableName() + " создана",
                executionTimeMs
        );
    }
}