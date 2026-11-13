package org.example;
import org.example.exception.DatabaseException;
import org.example.model.QueryResult;
import org.example.storage.Database;

/**
 * Команда CREATE INDEX для создания индекса по указанному столбцу таблицы.
 * Индекс позволяет ускорить поиск записей по значениям этого столбца.
 */
public class CreateIndexCommand implements QueryCommand {
    private final String indexName;
    private final String tableName;
    private final String columnName;
    /**
     * Создаёт команду CREATE INDEX.
     *
     * @param indexName имя создаваемого индекса
     * @param tableName имя таблицы
     * @param columnName имя столбца, по которому создаётся индекс
     * @throws SqlException если один из аргументов не указан
     */
    public CreateIndexCommand(String indexName, String tableName, String columnName) {
        if (indexName == null || indexName.isBlank()) {
            throw new SqlException("Не указано имя индекса");
        }
        if (tableName == null || tableName.isBlank()) {
            throw new SqlException("Не указано имя таблицы");
        }
        if (columnName == null || columnName.isBlank()) {
            throw new SqlException("Не указано имя столбца");
        }
        this.indexName = indexName;
        this.tableName = tableName;
        this.columnName = columnName;
    }
    /**
     * Проверяет существование таблицы и столбца, затем создаёт индекс.
     *
     * @param db база данных, в которой создаётся индекс
     * @return результат выполнения команды
     * @throws SqlException если таблица или столбец не найдены
     *                      либо произошла ошибка при создании индекса
     */
    @Override
    public QueryResult execute(Database db) throws DatabaseException {
        long start = System.currentTimeMillis();
        if (!db.hasTable(tableName)) {
            throw new SqlException("Таблица не найдена: " + tableName);
        }
        if (!db.getTable(tableName).getSchema().hasColumn(columnName)) {
            throw new SqlException("Столбец не найден: " + columnName);
        }
        db.createIndex(indexName, tableName, columnName);
        long executionTimeMs = System.currentTimeMillis() - start;
        return QueryResult.forDdl("Индекс " + indexName + " создан", executionTimeMs);
    }
}