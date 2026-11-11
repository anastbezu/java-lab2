package org.example.storage;

import org.example.exception.DatabaseException;

import java.util.List;

/**
 * Хранилище таблиц (паттерн DAO / Repository).
 * Скрывает, как именно таблицы сохраняются: остальной код работает только с этим интерфейсом.
 */
public interface TableRepository {
    /**
     * Сохраняет таблицу.
     */
    void save(Table table) throws DatabaseException;

    /**
     * Загружает таблицу по имени.
     *
     * @return таблица или null, если её нет
     */
    Table load(String tableName) throws DatabaseException;

    /**
     * Удаляет сохранённую таблицу.
     */
    void delete(String tableName) throws DatabaseException;

    /**
     * Возвращает имена всех сохранённых таблиц.
     */
    List<String> listTableNames();
}
