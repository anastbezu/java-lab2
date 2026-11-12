package org.example;
import org.example.storage.Database;
import org.example.model.QueryResult;
/**
 * Интерфейс для выполнения SQL-команд.
 * Каждая команда реализует метод execute(), который выполняет
 * операцию с базой данных и возвращает результат.
 */
public interface QueryCommand {
    /**
     * Выполняет команду над базой данных.
     *
     * @param db база данных, над которой выполняется команда
     * @return результат выполнения команды
     * @throws Exception если при выполнении команды произошла ошибка
     */
    QueryResult execute(Database db) throws Exception;
}
