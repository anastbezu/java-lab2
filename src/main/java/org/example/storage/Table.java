package org.example.storage;

import org.example.exception.DatabaseException;
import org.example.model.Column;
import org.example.model.Row;
import org.example.model.TableSchema;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Таблица: схема и строки в памяти.
 * <p>
 * Строки лежат в {@code LinkedHashMap}: ключ — значение первичного ключа, поэтому поиск по id
 * работает за O(1), а порядок вставки сохраняется. Таблица реализует {@link Iterable}
 * (паттерн Iterator), поэтому её можно обходить циклом {@code for (Row row : table)}.
 * <p>
 * Методы изменения данных закрыты от других пакетов: данные меняются только через
 * {@link Database}, который ещё проверяет внешние ключи, обновляет индексы и сохраняет таблицу.
 */
public class Table implements Serializable, Iterable<Row> {
    private static final long serialVersionUID = 1L;

    private final TableSchema schema;
    private final Map<Object, Row> rows = new LinkedHashMap<>();
    private final Map<String, String> indexes = new LinkedHashMap<>();

    public Table(TableSchema schema) {
        this.schema = schema;
    }

    public String getName() {
        return schema.getTableName();
    }

    public TableSchema getSchema() {
        return schema;
    }

    public int size() {
        return rows.size();
    }

    /**
     * Ищет строку по первичному ключу.
     *
     * @return копия строки или null, если её нет
     */
    public Row getById(Object primaryKey) {
        Row row = rows.get(primaryKey);
        return row == null ? null : row.copy();
    }

    public boolean containsKey(Object primaryKey) {
        return rows.containsKey(primaryKey);
    }

    /**
     * Выбирает строки по условию (SELECT).
     *
     * @param columns   нужные колонки; null или ["*"] — все колонки
     * @param condition условие WHERE; null — все строки
     * @return копии подходящих строк
     * @throws DatabaseException если запрошена несуществующая колонка
     */
    public List<Row> select(List<String> columns, Predicate<Row> condition) throws DatabaseException {
        checkColumnsExist(columns);
        List<Row> result = new ArrayList<>();
        for (Row row : rows.values()) {
            if (condition == null || condition.test(row)) {
                result.add(project(row, columns));
            }
        }
        return result;
    }

    /**
     * Обход таблицы циклом for-each. Отдаются копии строк, чтобы их нельзя было изменить в обход проверок.
     */
    @Override
    public Iterator<Row> iterator() {
        List<Row> copies = new ArrayList<>();
        for (Row row : rows.values()) {
            copies.add(row.copy());
        }
        return copies.iterator();
    }

    /**
     * Вставляет одну или несколько строк. Сначала проверяются все строки,
     * и только потом они добавляются: при ошибке не вставится ни одна.
     *
     * @throws DatabaseException если строка не подходит к схеме или первичный ключ повторяется
     */
    void insertAll(List<Row> newRows) throws DatabaseException {
        String pk = schema.getPrimaryKeyColumnName();
        Map<Object, Row> checked = new LinkedHashMap<>();
        for (Row row : newRows) {
            schema.validateRow(row);
            Object key = row.get(pk);
            if (rows.containsKey(key) || checked.containsKey(key)) {
                throw new DatabaseException("Запись с " + pk + " = " + key + " уже есть в таблице '" + getName() + "'");
            }
            checked.put(key, fillAllColumns(row));
        }
        rows.putAll(checked);
    }

    /**
     * Меняет значения колонок у строк, подходящих под условие (UPDATE).
     * Первичный ключ менять нельзя. Сначала проверяются все новые строки, потом они записываются.
     *
     * @param condition условие WHERE; null — все строки
     * @param newValues колонка -> новое значение
     * @return сколько строк изменено
     * @throws DatabaseException если колонки нет, тип не подходит или меняется первичный ключ
     */
    int update(Predicate<Row> condition, Map<String, Object> newValues) throws DatabaseException {
        for (String colName : newValues.keySet()) {
            if (schema.getPrimaryKeyColumnName().equalsIgnoreCase(colName.trim())) {
                throw new DatabaseException("Первичный ключ '" + colName + "' нельзя изменить через UPDATE");
            }
        }

        Map<Object, Row> updated = new LinkedHashMap<>();
        for (Map.Entry<Object, Row> entry : rows.entrySet()) {
            if (condition == null || condition.test(entry.getValue())) {
                Row newRow = entry.getValue().copy();
                for (Map.Entry<String, Object> value : newValues.entrySet()) {
                    newRow.set(value.getKey(), value.getValue());
                }
                schema.validateRow(newRow);
                updated.put(entry.getKey(), newRow);
            }
        }
        rows.putAll(updated);
        return updated.size();
    }

    /**
     * Удаляет строки, подходящие под условие (DELETE).
     *
     * @param condition условие WHERE; null — все строки
     * @return сколько строк удалено
     */
    int delete(Predicate<Row> condition) {
        int before = rows.size();
        rows.values().removeIf(row -> condition == null || condition.test(row));
        return before - rows.size();
    }

    /**
     * Индексы таблицы: имя индекса -> колонка.
     */
    Map<String, String> getIndexes() {
        return indexes;
    }

    /**
     * Оставляет в строке только нужные колонки (SELECT brand, price).
     */
    Row project(Row row, List<String> columns) {
        if (isAllColumns(columns)) {
            return row.copy();
        }
        Row result = new Row();
        for (String col : columns) {
            result.set(col, row.get(col));
        }
        return result;
    }

    void checkColumnsExist(List<String> columns) throws DatabaseException {
        if (isAllColumns(columns)) {
            return;
        }
        for (String col : columns) {
            if (!schema.hasColumn(col)) {
                throw new DatabaseException("Колонка '" + col + "' не найдена в таблице '" + getName() + "'");
            }
        }
    }

    private boolean isAllColumns(List<String> columns) {
        return columns == null || columns.isEmpty() || columns.contains("*");
    }

    /**
     * Делает строку, в которой есть все колонки схемы (незаполненные = NULL).
     */
    private Row fillAllColumns(Row row) {
        Row full = new Row();
        for (Column col : schema.getColumns()) {
            full.set(col.getName(), row.get(col.getName()));
        }
        return full;
    }

    @Override
    public String toString() {
        return "Table[" + getName() + ", rows=" + size() + "]";
    }
}
