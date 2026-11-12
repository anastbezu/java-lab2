package org.example;

import org.example.model.QueryResult;
import org.example.storage.Database;

/**
 * Команда CREATE INDEX для создания индекса по указанному столбцу таблицы.
 * Индекс позволяет ускорить поиск записей по значениям этого столбца.
 */
public class CreateIndexCommand implements QueryCommand {
    private String indexName;
    private String tableName;
    private String columnName;
    /**
     * Создаёт команду CREATE INDEX.
     *
     * @param indexName имя создаваемого индекса
     * @param tableName имя таблицы
     * @param columnName имя столбца, по которому создаётся индекс
     */
    public CreateIndexCommand(String indexName, String tableName, String columnName) {
        this.indexName = indexName;
        this.tableName = tableName;
        this.columnName = columnName;
    }

    /**
     * Проверяет существование таблицы и столбца, затем создаёт индекс.
     *
     * @param db база данных, в которой создаётся индекс
     * @return результат выполнения команды
     * @throws Exception если при создании индекса произошла ошибка
     */
    @Override
    public QueryResult execute(Database db) throws Exception {
        if (!db.hasTable(tableName)) {
            throw new IllegalArgumentException("Таблица не найдена: " + tableName);
        }
        if (!db.getTable(tableName).getSchema().hasColumn(columnName)) {
            throw new IllegalArgumentException("Столбец не найден: " + columnName);
        }
        long start = System.currentTimeMillis();
        db.createIndex(indexName, tableName, columnName);
        long executionTimeMs = System.currentTimeMillis() - start;
        return QueryResult.forDdl("Индекс " + indexName + " создан", executionTimeMs);
    }
}