package org.example.sql;

import org.example.exception.DatabaseException;
import org.example.model.QueryResult;
import org.example.storage.Database;

/**
 * Разобранный SQL-запрос, который можно выполнить над базой.
 */
public interface QueryCommand {

    /**
     * Выполняет команду.
     *
     * @param db база, над которой выполняется команда
     * @return результат для вывода в консоль
     * @throws DatabaseException если база отказалась выполнять команду
     */
    QueryResult execute(Database db) throws DatabaseException;
}
