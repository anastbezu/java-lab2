package org.example;
import org.example.exception.DatabaseException;
import org.example.model.QueryResult;
import org.example.model.TableSchema;
import org.example.storage.Database;
/**
 * Команда CREATE TABLE для создания новой таблицы.
 * Использует схему, содержащую названия и типы столбцов таблицы.
 */
public class CreateTableCommand implements QueryCommand {
    private final TableSchema schema;
    /**
     * Создаёт команду CREATE TABLE.
     *
     * @param schema схема создаваемой таблицы
     * @throws SqlException если схема не указана
     */
    public CreateTableCommand(TableSchema schema) {
        if (schema == null) {
            throw new SqlException("Не указана схема таблицы");
        }
        this.schema = schema;
    }
    /**
     * Создаёт таблицу в базе данных на основе переданной схемы.
     *
     * @param db база данных, в которой создаётся таблица
     * @return результат выполнения команды
     * @throws DatabaseException если таблицу не удалось создать
     */
    @Override
    public QueryResult execute(Database db) throws DatabaseException {
        if (db == null) {
            throw new SqlException("База данных не указана");
        }
        long start = System.currentTimeMillis();
        db.createTable(schema);
        long executionTimeMs = System.currentTimeMillis() - start;
        return QueryResult.forDdl("Таблица " + schema.getTableName() + " создана", executionTimeMs);
    }
}