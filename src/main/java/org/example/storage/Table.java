package org.example.storage;

import org.example.exception.DatabaseException;
import org.example.model.Column;
import org.example.model.Row;
import org.example.model.TableSchema;
import org.example.model.WhereCondition;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Таблица в памяти: схема и строки.
 * Строки хранятся по значению первичного ключа, порядок вставки сохраняется.
 * Таблицу можно обойти циклом for (Row row : table).
 * Читать таблицу можно напрямую, а менять данные - только через {@link Database},
 * потому что он ещё проверяет внешние ключи, обновляет индексы и сохраняет всё на диск.
 */
public class Table implements Serializable, Iterable<Row> {
    private static final long serialVersionUID = 1L;

    private final TableSchema schema;
    private final Map<Object, Row> rows = new LinkedHashMap<>();
    private final Map<String, String> indexes = new LinkedHashMap<>();

    /**
     * Создаёт пустую таблицу.
     */
    public Table(TableSchema schema) {
        this.schema = schema;
    }

    /**
     * Возвращает имя таблицы.
     */
    public String getName() {
        return schema.getTableName();
    }

    /**
     * Возвращает схему таблицы.
     */
    public TableSchema getSchema() {
        return schema;
    }

    /**
     * Возвращает количество строк.
     */
    public int size() {
        return rows.size();
    }

    /**
     * Ищет строку по первичному ключу.
     *
     * @return копия строки или null, если такой нет
     */
    public Row getById(Object primaryKey) {
        Row row = rows.get(primaryKey);
        if (row == null) {
            return null;
        }
        return row.copy();
    }

    /**
     * Есть ли строка с таким первичным ключом.
     */
    public boolean containsKey(Object primaryKey) {
        return rows.containsKey(primaryKey);
    }

    /**
     * Выбирает строки по условию (SELECT).
     *
     * @param columns   нужные колонки, null или ["*"] - все
     * @param condition условие WHERE, null - все строки
     * @return копии подходящих строк
     * @throws DatabaseException если запросили колонку, которой нет
     */
    public List<Row> select(List<String> columns, WhereCondition condition) throws DatabaseException {
        checkColumnsExist(columns);
        List<Row> result = new ArrayList<>();
        for (Row row : rows.values()) {
            if (condition == null || condition.matches(row)) {
                Row projected = project(row, columns);
                result.add(projected);
            }
        }
        return result;
    }

    /**
     * Выбирает строки, где колонка равна значению, просматривая всю таблицу.
     *
     * @param value   значение нужного типа, для null ничего не находится
     * @param columns нужные колонки, null или ["*"] - все
     * @return копии подходящих строк
     * @throws DatabaseException если запросили колонку, которой нет
     */
    List<Row> selectEquals(String columnName, Object value, List<String> columns) throws DatabaseException {
        checkColumnsExist(columns);
        List<Row> result = new ArrayList<>();
        if (value == null) {
            return result;
        }
        for (Row row : rows.values()) {
            Object cell = row.get(columnName);
            if (value.equals(cell)) {
                Row projected = project(row, columns);
                result.add(projected);
            }
        }
        return result;
    }

    /**
     * Отдаёт копии строк, чтобы их нельзя было поменять в обход проверок.
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
     * Вставляет строки. Сначала проверяются все, потом добавляются,
     * так что если хоть одна плохая, не вставится ни одна.
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
                throw new DatabaseException("В таблице '" + getName() + "' уже есть запись с " + pk + " = " + key);
            }
            Row fullRow = fillAllColumns(row);
            checked.put(key, fullRow);
        }
        rows.putAll(checked);
    }

    /**
     * Меняет значения у строк, подходящих под условие (UPDATE).
     * Первичный ключ менять нельзя. Новые строки сначала проверяются, потом записываются.
     *
     * @param condition условие WHERE, null - все строки
     * @param newValues колонка -> новое значение
     * @return сколько строк изменено
     * @throws DatabaseException если колонки нет, тип не подходит или пытаются поменять первичный ключ
     */
    int update(WhereCondition condition, Map<String, Object> newValues) throws DatabaseException {
        String pk = schema.getPrimaryKeyColumnName();
        for (String colName : newValues.keySet()) {
            if (pk.equalsIgnoreCase(colName.trim())) {
                throw new DatabaseException("Менять " + colName + " через UPDATE нельзя - это первичный ключ");
            }
        }

        Map<Object, Row> updated = new LinkedHashMap<>();
        for (Map.Entry<Object, Row> entry : rows.entrySet()) {
            Row oldRow = entry.getValue();
            if (condition == null || condition.matches(oldRow)) {
                Row newRow = oldRow.copy();
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
     * @param condition условие WHERE, null - все строки
     * @return сколько строк удалено
     */
    int delete(WhereCondition condition) {
        int removed = 0;
        Iterator<Row> iterator = rows.values().iterator();
        while (iterator.hasNext()) {
            Row row = iterator.next();
            if (condition == null || condition.matches(row)) {
                iterator.remove();
                removed++;
            }
        }
        return removed;
    }

    /**
     * Индексы таблицы: имя индекса -> колонка.
     */
    Map<String, String> getIndexes() {
        return indexes;
    }

    /**
     * Оставляет в строке только нужные колонки, как в SELECT brand, price.
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

    /**
     * Проверяет, что все запрошенные колонки есть в таблице.
     *
     * @throws DatabaseException если какой-то колонки нет
     */
    void checkColumnsExist(List<String> columns) throws DatabaseException {
        if (isAllColumns(columns)) {
            return;
        }
        for (String col : columns) {
            if (!schema.hasColumn(col)) {
                throw new DatabaseException("В таблице '" + getName() + "' нет колонки '" + col + "'");
            }
        }
    }

    private boolean isAllColumns(List<String> columns) {
        return columns == null || columns.isEmpty() || columns.contains("*");
    }

    /**
     * Делает строку со всеми колонками схемы, незаполненные будут NULL.
     */
    private Row fillAllColumns(Row row) {
        Row full = new Row();
        for (Column col : schema.getColumns()) {
            String name = col.getName();
            full.set(name, row.get(name));
        }
        return full;
    }

    @Override
    public String toString() {
        return "Table[" + getName() + ", rows=" + size() + "]";
    }
}
