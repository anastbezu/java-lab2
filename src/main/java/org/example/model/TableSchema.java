package org.example.model;

import org.example.exception.DatabaseException;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Схема таблицы: её имя и список колонок.
 * В каждой таблице ровно один первичный ключ.
 */
public class TableSchema implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String tableName;
    private final List<Column> columns;
    private Column primaryKeyColumn;

    /**
     * @param tableName имя таблицы
     * @param columns   колонки таблицы (ровно одна должна быть первичным ключом)
     * @throws IllegalArgumentException если колонок нет, есть повторы или первичный ключ не один
     */
    public TableSchema(String tableName, List<Column> columns) {
        this.tableName = tableName.trim();
        this.columns = new ArrayList<>(columns);

        if (columns.isEmpty()) {
            throw new IllegalArgumentException("Таблица должна содержать хотя бы одну колонку");
        }

        List<String> seenNames = new ArrayList<>();
        int pkCount = 0;
        for (Column col : columns) {
            String lowerName = col.getName().toLowerCase();
            if (seenNames.contains(lowerName)) {
                throw new IllegalArgumentException("Колонка '" + col.getName() + "' указана дважды");
            }
            seenNames.add(lowerName);

            if (col.isPrimaryKey()) {
                pkCount++;
                primaryKeyColumn = col;
            }
        }
        if (pkCount != 1) {
            throw new IllegalArgumentException("Таблица '" + tableName
                    + "' должна иметь ровно один первичный ключ (найдено " + pkCount + ")");
        }
    }

    public String getTableName() {
        return tableName;
    }

    public List<Column> getColumns() {
        return columns;
    }

    public List<String> getColumnNames() {
        List<String> names = new ArrayList<>();
        for (Column col : columns) {
            names.add(col.getName());
        }
        return names;
    }

    /**
     * Ищет колонку по имени без учёта регистра.
     *
     * @return колонка или null, если такой нет
     */
    public Column getColumn(String name) {
        for (Column col : columns) {
            if (col.getName().equalsIgnoreCase(name.trim())) {
                return col;
            }
        }
        return null;
    }

    public boolean hasColumn(String name) {
        return getColumn(name) != null;
    }

    public Column getPrimaryKeyColumn() {
        return primaryKeyColumn;
    }

    public String getPrimaryKeyColumnName() {
        return primaryKeyColumn.getName();
    }

    /**
     * Проверяет строку: первичный ключ заполнен, нет лишних колонок, все значения нужного типа.
     *
     * @throws DatabaseException если строка не подходит к схеме
     */
    public void validateRow(Row row) throws DatabaseException {
        if (row.get(getPrimaryKeyColumnName()) == null) {
            throw new DatabaseException("Первичный ключ '" + getPrimaryKeyColumnName() + "' не может быть NULL");
        }
        for (String colName : row.getValues().keySet()) {
            if (!hasColumn(colName)) {
                throw new DatabaseException("Колонка '" + colName + "' не найдена в таблице '" + tableName + "'");
            }
        }
        for (Column col : columns) {
            Object value = row.get(col.getName());
            if (!col.getType().isCompatible(value)) {
                throw new DatabaseException("Колонка '" + col.getName() + "' ожидает тип " + col.getType()
                        + ", а получено значение '" + value + "'");
            }
        }
    }

    /**
     * Создаёт строку для {@code INSERT INTO t VALUES (v1, v2, ...)}: значения идут по порядку колонок.
     *
     * @param rawValues значения из запроса в виде текста
     * @throws DatabaseException если число значений не совпадает с числом колонок или тип не подходит
     */
    public Row createRowFromPositionalValues(List<String> rawValues) throws DatabaseException {
        if (rawValues.size() != columns.size()) {
            throw new DatabaseException("Таблица '" + tableName + "' содержит " + columns.size()
                    + " колонок, а передано значений: " + rawValues.size());
        }
        List<String> names = getColumnNames();
        return createRowFromNamedValues(names, rawValues);
    }

    /**
     * Создаёт строку для {@code INSERT INTO t (c1, c2) VALUES (v1, v2)}.
     * Колонки, которых нет в списке, получают значение NULL.
     *
     * @param columnNames имена колонок из запроса
     * @param rawValues   значения из запроса в виде текста
     * @throws DatabaseException если колонки нет, число значений не совпадает или тип не подходит
     */
    public Row createRowFromNamedValues(List<String> columnNames, List<String> rawValues) throws DatabaseException {
        if (columnNames.size() != rawValues.size()) {
            throw new DatabaseException("Колонок " + columnNames.size() + ", а значений " + rawValues.size());
        }
        Row row = new Row();
        for (int i = 0; i < columnNames.size(); i++) {
            Column col = getColumn(columnNames.get(i));
            if (col == null) {
                throw new DatabaseException("Колонка '" + columnNames.get(i) + "' не найдена в таблице '" + tableName + "'");
            }
            try {
                row.set(col.getName(), col.getType().parse(rawValues.get(i)));
            } catch (IllegalArgumentException e) {
                throw new DatabaseException(e.getMessage());
            }
        }
        validateRow(row);
        return row;
    }

    @Override
    public String toString() {
        return tableName + columns;
    }
}
