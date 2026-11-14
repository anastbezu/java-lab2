package org.example.storage;

import org.example.exception.DatabaseException;

import java.util.List;

/**
 * Хранилище таблиц (паттерн Repository).
 * Database работает только с этим интерфейсом и не знает, как именно таблицы лежат на диске.
 */
public interface TableRepository {

    /**
     * Сохраняет таблицу.
     *
     * @throws DatabaseException если сохранить не получилось
     */
    void save(Table table) throws DatabaseException;

    /**
     * Загружает таблицу по имени.
     *
     * @return таблица или null, если её нет
     * @throws DatabaseException если таблица есть, но прочитать её не получилось
     */
    Table load(String tableName) throws DatabaseException;

    /**
     * Удаляет сохранённую таблицу.
     */
    void delete(String tableName);

    /**
     * Возвращает имена всех сохранённых таблиц.
     */
    List<String> listTableNames();
}
